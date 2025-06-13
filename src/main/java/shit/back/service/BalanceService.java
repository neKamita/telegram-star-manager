package shit.back.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import shit.back.entity.*;
import shit.back.repository.BalanceTransactionJpaRepository;
import shit.back.repository.UserBalanceJpaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Основной сервис управления балансом пользователей
 * 
 * Обеспечивает безопасные финансовые операции с ACID свойствами,
 * pessimistic locking для предотвращения race conditions,
 * и comprehensive audit trail для всех операций.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceService {

    private final UserBalanceJpaRepository userBalanceRepository;
    private final BalanceTransactionJpaRepository transactionRepository;
    private final BalanceTransactionService transactionService;
    private final BalanceValidationService validationService;
    private final UserSessionService userSessionService;

    // Защита от конкурентных операций для одного пользователя
    private final Map<Long, Object> userLocks = new ConcurrentHashMap<>();

    @Value("${balance.default-currency:USD}")
    private String defaultCurrency;

    @Value("${balance.max-concurrent-operations:5}")
    private int maxConcurrentOperations;

    /**
     * Получение или создание баланса пользователя
     * 
     * @param userId ID пользователя
     * @return баланс пользователя
     */
    @Transactional
    public UserBalanceEntity getOrCreateBalance(Long userId) {
        log.debug("Получение или создание баланса для пользователя: {}", userId);

        try {
            return userBalanceRepository.findByUserId(userId)
                    .orElseGet(() -> createNewBalance(userId));
        } catch (Exception e) {
            log.error("Ошибка при получении/создании баланса для пользователя {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Не удалось получить баланс пользователя", e);
        }
    }

    /**
     * Пополнение баланса пользователя
     * 
     * @param userId        ID пользователя
     * @param amount        сумма пополнения
     * @param paymentMethod способ платежа
     * @param description   описание операции
     * @return созданная транзакция
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BalanceTransactionEntity deposit(Long userId, BigDecimal amount, String paymentMethod, String description) {
        log.info("Пополнение баланса пользователя {}: {} ({})", userId, amount, paymentMethod);

        // Валидация входных параметров
        validationService.validateDepositAmount(amount);
        validationService.validateConcurrentOperations(userId);

        Object userLock = userLocks.computeIfAbsent(userId, k -> new Object());

        synchronized (userLock) {
            try {
                // Получаем баланс с pessimistic lock
                UserBalanceEntity balance = getOrCreateBalance(userId);
                BigDecimal balanceBefore = balance.getCurrentBalance();

                // Выполняем пополнение
                balance.deposit(amount);
                userBalanceRepository.save(balance);

                // Создаем транзакцию
                BalanceTransactionEntity transaction = transactionService.createTransaction(
                        userId, TransactionType.DEPOSIT, amount, description);
                transaction.setBalanceBefore(balanceBefore);
                transaction.setBalanceAfter(balance.getCurrentBalance());
                transaction.setPaymentInfo(paymentMethod, null);
                transaction.complete();

                transactionRepository.save(transaction);

                log.info("Пополнение завершено для пользователя {}: {} -> {}",
                        userId, balanceBefore, balance.getCurrentBalance());

                return transaction;

            } catch (Exception e) {
                log.error("Ошибка при пополнении баланса пользователя {}: {}", userId, e.getMessage(), e);
                throw new RuntimeException("Не удалось пополнить баланс", e);
            } finally {
                userLocks.remove(userId);
            }
        }
    }

    /**
     * Списание с баланса
     * 
     * @param userId      ID пользователя
     * @param amount      сумма списания
     * @param description описание операции
     * @return созданная транзакция
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BalanceTransactionEntity withdraw(Long userId, BigDecimal amount, String description) {
        log.info("Списание с баланса пользователя {}: {}", userId, amount);

        // Валидация
        validationService.validateWithdrawalAmount(userId, amount);
        validationService.validateConcurrentOperations(userId);

        Object userLock = userLocks.computeIfAbsent(userId, k -> new Object());

        synchronized (userLock) {
            try {
                UserBalanceEntity balance = getOrCreateBalance(userId);
                BigDecimal balanceBefore = balance.getCurrentBalance();

                // Проверка достаточности средств
                if (!balance.hasSufficientFunds(amount)) {
                    log.warn("Недостаточно средств для пользователя {}: требуется {}, доступно {}",
                            userId, amount, balance.getCurrentBalance());
                    throw new RuntimeException("Недостаточно средств на балансе");
                }

                // Выполняем списание
                boolean success = balance.withdraw(amount);
                if (!success) {
                    throw new RuntimeException("Не удалось списать средства с баланса");
                }

                userBalanceRepository.save(balance);

                // Создаем транзакцию
                BalanceTransactionEntity transaction = transactionService.createTransaction(
                        userId, TransactionType.WITHDRAWAL, amount, description);
                transaction.setBalanceBefore(balanceBefore);
                transaction.setBalanceAfter(balance.getCurrentBalance());
                transaction.complete();

                transactionRepository.save(transaction);

                log.info("Списание завершено для пользователя {}: {} -> {}",
                        userId, balanceBefore, balance.getCurrentBalance());

                return transaction;

            } catch (Exception e) {
                log.error("Ошибка при списании с баланса пользователя {}: {}", userId, e.getMessage(), e);
                throw e;
            } finally {
                userLocks.remove(userId);
            }
        }
    }

    /**
     * Проверка достаточности средств на балансе
     * 
     * @param userId ID пользователя
     * @param amount требуемая сумма
     * @return true если средств достаточно
     */
    @Transactional(readOnly = true)
    public boolean checkSufficientBalance(Long userId, BigDecimal amount) {
        log.debug("Проверка достаточности средств для пользователя {}: {}", userId, amount);

        try {
            UserBalanceEntity balance = getOrCreateBalance(userId);
            boolean sufficient = balance.hasSufficientFunds(amount);

            log.debug("Результат проверки для пользователя {}: {} (баланс: {})",
                    userId, sufficient, balance.getCurrentBalance());

            return sufficient;
        } catch (Exception e) {
            log.error("Ошибка при проверке баланса пользователя {}: {}", userId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Резервирование средств для заказа
     * 
     * @param userId  ID пользователя
     * @param amount  сумма резервирования
     * @param orderId ID заказа
     * @return созданная транзакция резервирования
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BalanceTransactionEntity reserveBalance(Long userId, BigDecimal amount, String orderId) {
        log.info("Резервирование средств для пользователя {}: {} (заказ: {})", userId, amount, orderId);

        // Валидация
        validationService.validateWithdrawalAmount(userId, amount);
        validationService.validateConcurrentOperations(userId);

        Object userLock = userLocks.computeIfAbsent(userId, k -> new Object());

        synchronized (userLock) {
            try {
                UserBalanceEntity balance = getOrCreateBalance(userId);
                BigDecimal balanceBefore = balance.getCurrentBalance();

                // Проверка достаточности средств
                if (!balance.hasSufficientFunds(amount)) {
                    log.warn("Недостаточно средств для резервирования пользователю {}: требуется {}, доступно {}",
                            userId, amount, balance.getCurrentBalance());
                    throw new RuntimeException("Недостаточно средств для резервирования");
                }

                // Создаем транзакцию резервирования (пока не списываем с баланса)
                BalanceTransactionEntity transaction = transactionService.createTransaction(
                        userId, TransactionType.PURCHASE, amount, "Резервирование для заказа " + orderId);
                transaction.setBalanceBefore(balanceBefore);
                transaction.setBalanceAfter(balanceBefore); // Баланс пока не изменился
                transaction.setOrderId(orderId);
                transaction.setStatus(TransactionStatus.PENDING);

                transactionRepository.save(transaction);

                log.info("Средства зарезервированы для пользователя {}: {} (транзакция: {})",
                        userId, amount, transaction.getTransactionId());

                return transaction;

            } catch (Exception e) {
                log.error("Ошибка при резервировании средств пользователя {}: {}", userId, e.getMessage(), e);
                throw e;
            } finally {
                userLocks.remove(userId);
            }
        }
    }

    /**
     * Освобождение зарезервированных средств
     * 
     * @param userId  ID пользователя
     * @param orderId ID заказа
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void releaseReservedBalance(Long userId, String orderId) {
        log.info("Освобождение зарезервированных средств для пользователя {} (заказ: {})", userId, orderId);

        Object userLock = userLocks.computeIfAbsent(userId, k -> new Object());

        synchronized (userLock) {
            try {
                // Найти транзакцию резервирования
                List<BalanceTransactionEntity> orderTransactions = transactionRepository
                        .findByOrderIdOrderByCreatedAtDesc(orderId);

                for (BalanceTransactionEntity transaction : orderTransactions) {
                    if (transaction.getUserId().equals(userId) &&
                            transaction.getStatus() == TransactionStatus.PENDING &&
                            transaction.getType() == TransactionType.PURCHASE) {

                        // Отменяем транзакцию резервирования
                        transaction.cancel();
                        transactionRepository.save(transaction);

                        log.info("Освобождены зарезервированные средства для пользователя {} (транзакция: {})",
                                userId, transaction.getTransactionId());
                        break;
                    }
                }

            } catch (Exception e) {
                log.error("Ошибка при освобождении зарезервированных средств пользователя {}: {}", userId,
                        e.getMessage(), e);
                throw new RuntimeException("Не удалось освободить зарезервированные средства", e);
            } finally {
                userLocks.remove(userId);
            }
        }
    }

    /**
     * Обработка оплаты балансом (списание зарезервированных средств)
     * 
     * @param userId  ID пользователя
     * @param orderId ID заказа
     * @param amount  сумма к списанию
     * @return транзакция покупки
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BalanceTransactionEntity processBalancePayment(Long userId, String orderId, BigDecimal amount) {
        log.info("Обработка оплаты балансом для пользователя {}: {} (заказ: {})", userId, amount, orderId);

        Object userLock = userLocks.computeIfAbsent(userId, k -> new Object());

        synchronized (userLock) {
            try {
                UserBalanceEntity balance = getOrCreateBalance(userId);
                BigDecimal balanceBefore = balance.getCurrentBalance();

                // Списываем средства с баланса
                boolean success = balance.withdraw(amount);
                if (!success) {
                    throw new RuntimeException("Не удалось списать средства с баланса");
                }

                userBalanceRepository.save(balance);

                // Обновляем транзакцию резервирования или создаем новую
                List<BalanceTransactionEntity> orderTransactions = transactionRepository
                        .findByOrderIdOrderByCreatedAtDesc(orderId);
                BalanceTransactionEntity transaction = null;

                // Ищем pending транзакцию резервирования
                for (BalanceTransactionEntity t : orderTransactions) {
                    if (t.getUserId().equals(userId) &&
                            t.getStatus() == TransactionStatus.PENDING &&
                            t.getType() == TransactionType.PURCHASE) {
                        transaction = t;
                        break;
                    }
                }

                if (transaction != null) {
                    // Обновляем существующую транзакцию
                    transaction.setBalanceBefore(balanceBefore);
                    transaction.setBalanceAfter(balance.getCurrentBalance());
                    transaction.complete();
                } else {
                    // Создаем новую транзакцию покупки
                    transaction = transactionService.createTransaction(
                            userId, TransactionType.PURCHASE, amount, "Покупка по заказу " + orderId);
                    transaction.setBalanceBefore(balanceBefore);
                    transaction.setBalanceAfter(balance.getCurrentBalance());
                    transaction.setOrderId(orderId);
                    transaction.complete();
                }

                transactionRepository.save(transaction);

                log.info("Оплата балансом завершена для пользователя {}: {} -> {} (заказ: {})",
                        userId, balanceBefore, balance.getCurrentBalance(), orderId);

                return transaction;

            } catch (Exception e) {
                log.error("Ошибка при обработке оплаты балансом пользователя {}: {}", userId, e.getMessage(), e);
                throw e;
            } finally {
                userLocks.remove(userId);
            }
        }
    }

    /**
     * Возврат средств на баланс
     * 
     * @param userId      ID пользователя
     * @param amount      сумма возврата
     * @param orderId     ID заказа (если применимо)
     * @param description описание возврата
     * @return транзакция возврата
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BalanceTransactionEntity refundToBalance(Long userId, BigDecimal amount, String orderId,
            String description) {
        log.info("Возврат средств на баланс пользователя {}: {} (заказ: {})", userId, amount, orderId);

        // Валидация
        validationService.validateDepositAmount(amount);
        validationService.validateConcurrentOperations(userId);

        Object userLock = userLocks.computeIfAbsent(userId, k -> new Object());

        synchronized (userLock) {
            try {
                UserBalanceEntity balance = getOrCreateBalance(userId);
                BigDecimal balanceBefore = balance.getCurrentBalance();

                // Выполняем возврат
                balance.refund(amount);
                userBalanceRepository.save(balance);

                // Создаем транзакцию возврата
                BalanceTransactionEntity transaction = transactionService.createTransaction(
                        userId, TransactionType.REFUND, amount, description);
                transaction.setBalanceBefore(balanceBefore);
                transaction.setBalanceAfter(balance.getCurrentBalance());
                if (orderId != null) {
                    transaction.setOrderId(orderId);
                }
                transaction.complete();

                transactionRepository.save(transaction);

                log.info("Возврат завершен для пользователя {}: {} -> {}",
                        userId, balanceBefore, balance.getCurrentBalance());

                return transaction;

            } catch (Exception e) {
                log.error("Ошибка при возврате средств пользователю {}: {}", userId, e.getMessage(), e);
                throw new RuntimeException("Не удалось выполнить возврат средств", e);
            } finally {
                userLocks.remove(userId);
            }
        }
    }

    /**
     * Административная корректировка баланса
     * 
     * @param userId    ID пользователя
     * @param amount    сумма корректировки (может быть отрицательной)
     * @param reason    причина корректировки
     * @param adminUser администратор, выполняющий операцию
     * @return транзакция корректировки
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BalanceTransactionEntity adjustBalance(Long userId, BigDecimal amount, String reason, String adminUser) {
        log.info("Административная корректировка баланса пользователя {}: {} (админ: {}, причина: {})",
                userId, amount, adminUser, reason);

        // Валидация административной операции
        validationService.validateAdminOperation(adminUser, "BALANCE_ADJUSTMENT");
        validationService.validateConcurrentOperations(userId);

        Object userLock = userLocks.computeIfAbsent(userId, k -> new Object());

        synchronized (userLock) {
            try {
                UserBalanceEntity balance = getOrCreateBalance(userId);
                BigDecimal balanceBefore = balance.getCurrentBalance();

                // Выполняем корректировку
                balance.adjustBalance(amount, reason);
                userBalanceRepository.save(balance);

                // Создаем транзакцию корректировки
                BalanceTransactionEntity transaction = transactionService.createTransaction(
                        userId, TransactionType.ADJUSTMENT, amount.abs(),
                        "Админская корректировка: " + reason);
                transaction.setBalanceBefore(balanceBefore);
                transaction.setBalanceAfter(balance.getCurrentBalance());
                transaction.setProcessedBy(adminUser);
                transaction.complete();

                transactionRepository.save(transaction);

                log.info("Административная корректировка завершена для пользователя {}: {} -> {} (админ: {})",
                        userId, balanceBefore, balance.getCurrentBalance(), adminUser);

                return transaction;

            } catch (Exception e) {
                log.error("Ошибка при административной корректировке баланса пользователя {}: {}", userId,
                        e.getMessage(), e);
                throw e;
            } finally {
                userLocks.remove(userId);
            }
        }
    }

    /**
     * Получение истории операций с балансом
     * 
     * @param userId ID пользователя
     * @param limit  количество записей для выборки
     * @return список транзакций
     */
    @Transactional(readOnly = true)
    public List<BalanceTransactionEntity> getBalanceHistory(Long userId, int limit) {
        log.debug("Получение истории баланса для пользователя {}: последние {} операций", userId, limit);

        try {
            Pageable pageable = PageRequest.of(0, limit);
            return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable).getContent();
        } catch (Exception e) {
            log.error("Ошибка при получении истории баланса пользователя {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Не удалось получить историю операций", e);
        }
    }

    /**
     * Получение статистики по балансу пользователя
     * 
     * @param userId ID пользователя
     * @return статистика в виде Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getBalanceStatistics(Long userId) {
        log.debug("Получение статистики баланса для пользователя: {}", userId);

        try {
            UserBalanceEntity balance = getOrCreateBalance(userId);
            List<Object[]> transactionStats = transactionRepository.getUserTransactionStatistics(userId);
            Long transactionCount = transactionRepository.countByUserId(userId);

            Map<String, Object> stats = Map.of(
                    "currentBalance", balance.getCurrentBalance(),
                    "totalDeposited", balance.getTotalDeposited(),
                    "totalSpent", balance.getTotalSpent(),
                    "totalTurnover", balance.getTotalTurnover(),
                    "currency", balance.getCurrency(),
                    "transactionCount", transactionCount,
                    "isActive", balance.getIsActive(),
                    "lastUpdated", balance.getLastUpdated(),
                    "createdAt", balance.getCreatedAt());

            log.debug("Статистика для пользователя {}: баланс={}, операций={}",
                    userId, balance.getCurrentBalance(), transactionCount);

            return stats;

        } catch (Exception e) {
            log.error("Ошибка при получении статистики баланса пользователя {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Не удалось получить статистику баланса", e);
        }
    }

    /**
     * Создание нового баланса для пользователя
     */
    private UserBalanceEntity createNewBalance(Long userId) {
        log.info("Создание нового баланса для пользователя: {}", userId);

        try {
            UserBalanceEntity balance = new UserBalanceEntity(userId, defaultCurrency);
            UserBalanceEntity savedBalance = userBalanceRepository.save(balance);

            log.info("Создан новый баланс для пользователя {}: ID={}", userId, savedBalance.getId());
            return savedBalance;

        } catch (Exception e) {
            log.error("Ошибка при создании баланса для пользователя {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Не удалось создать баланс пользователя", e);
        }
    }
}