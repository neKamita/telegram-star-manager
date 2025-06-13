package shit.back.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shit.back.entity.BalanceTransactionEntity;
import shit.back.entity.TransactionStatus;
import shit.back.entity.TransactionType;
import shit.back.repository.BalanceTransactionJpaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Сервис управления транзакциями баланса
 * 
 * Обеспечивает создание, обновление и мониторинг транзакций,
 * предоставляет методы для анализа и отчетности.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceTransactionService {

    private final BalanceTransactionJpaRepository transactionRepository;

    /**
     * Создание новой транзакции
     * 
     * @param userId      ID пользователя
     * @param type        тип транзакции
     * @param amount      сумма транзакции
     * @param description описание
     * @return созданная транзакция
     */
    @Transactional
    public BalanceTransactionEntity createTransaction(Long userId, TransactionType type,
            BigDecimal amount, String description) {
        log.debug("Создание транзакции для пользователя {}: тип={}, сумма={}", userId, type, amount);

        try {
            // Создаем транзакцию с временными значениями баланса (будут обновлены позже)
            BalanceTransactionEntity transaction = new BalanceTransactionEntity(
                    userId, type, amount, BigDecimal.ZERO, BigDecimal.ZERO, description);

            BalanceTransactionEntity savedTransaction = transactionRepository.save(transaction);

            log.info("Создана транзакция {}: пользователь={}, тип={}, сумма={}",
                    savedTransaction.getTransactionId(), userId, type, amount);

            return savedTransaction;

        } catch (Exception e) {
            log.error("Ошибка при создании транзакции для пользователя {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Не удалось создать транзакцию", e);
        }
    }

    /**
     * Создание транзакции с привязкой к заказу
     * 
     * @param userId      ID пользователя
     * @param type        тип транзакции
     * @param amount      сумма транзакции
     * @param description описание
     * @param orderId     ID заказа
     * @return созданная транзакция
     */
    @Transactional
    public BalanceTransactionEntity createTransactionWithOrder(Long userId, TransactionType type,
            BigDecimal amount, String description, String orderId) {
        log.debug("Создание транзакции с заказом для пользователя {}: тип={}, сумма={}, заказ={}",
                userId, type, amount, orderId);

        try {
            BalanceTransactionEntity transaction = new BalanceTransactionEntity(
                    userId, type, amount, BigDecimal.ZERO, BigDecimal.ZERO, description, orderId);

            BalanceTransactionEntity savedTransaction = transactionRepository.save(transaction);

            log.info("Создана транзакция с заказом {}: пользователь={}, заказ={}, тип={}, сумма={}",
                    savedTransaction.getTransactionId(), userId, orderId, type, amount);

            return savedTransaction;

        } catch (Exception e) {
            log.error("Ошибка при создании транзакции с заказом для пользователя {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Не удалось создать транзакцию с заказом", e);
        }
    }

    /**
     * Обновление статуса транзакции
     * 
     * @param transactionId ID транзакции
     * @param status        новый статус
     * @return обновленная транзакция
     */
    @Transactional
    public Optional<BalanceTransactionEntity> updateTransactionStatus(String transactionId, TransactionStatus status) {
        log.debug("Обновление статуса транзакции {}: новый статус={}", transactionId, status);

        try {
            Optional<BalanceTransactionEntity> transactionOpt = transactionRepository
                    .findByTransactionId(transactionId);

            if (transactionOpt.isPresent()) {
                BalanceTransactionEntity transaction = transactionOpt.get();
                TransactionStatus oldStatus = transaction.getStatus();

                // Обновляем статус в зависимости от нового значения
                switch (status) {
                    case COMPLETED -> transaction.complete();
                    case CANCELLED -> transaction.cancel();
                    case FAILED -> transaction.fail();
                    case PENDING -> transaction.setStatus(TransactionStatus.PENDING);
                }

                transactionRepository.save(transaction);

                log.info("Обновлен статус транзакции {}: {} -> {}",
                        transactionId, oldStatus, status);

                return Optional.of(transaction);
            } else {
                log.warn("Транзакция не найдена: {}", transactionId);
                return Optional.empty();
            }

        } catch (Exception e) {
            log.error("Ошибка при обновлении статуса транзакции {}: {}", transactionId, e.getMessage(), e);
            throw new RuntimeException("Не удалось обновить статус транзакции", e);
        }
    }

    /**
     * Получение транзакции по ID
     * 
     * @param transactionId ID транзакции
     * @return транзакция если найдена
     */
    @Transactional(readOnly = true)
    public Optional<BalanceTransactionEntity> getTransactionById(String transactionId) {
        log.debug("Поиск транзакции по ID: {}", transactionId);

        try {
            return transactionRepository.findByTransactionId(transactionId);
        } catch (Exception e) {
            log.error("Ошибка при поиске транзакции {}: {}", transactionId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Получение транзакций пользователя с фильтрами
     * 
     * @param userId  ID пользователя
     * @param filters фильтры для поиска
     * @return страница транзакций
     */
    @Transactional(readOnly = true)
    public Page<BalanceTransactionEntity> getUserTransactions(Long userId, Map<String, Object> filters) {
        log.debug("Получение транзакций пользователя {} с фильтрами: {}", userId, filters);

        try {
            int page = (Integer) filters.getOrDefault("page", 0);
            int size = (Integer) filters.getOrDefault("size", 20);
            TransactionType type = (TransactionType) filters.get("type");
            TransactionStatus status = (TransactionStatus) filters.get("status");
            LocalDateTime dateFrom = (LocalDateTime) filters.get("dateFrom");
            LocalDateTime dateTo = (LocalDateTime) filters.get("dateTo");

            Pageable pageable = PageRequest.of(page, size);

            // Применяем фильтры
            if (type != null) {
                return transactionRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, type, pageable);
            } else if (status != null) {
                // Преобразуем List в Page для совместимости
                List<BalanceTransactionEntity> statusTransactions = transactionRepository
                        .findByUserIdAndStatusOrderByCreatedAtDesc(userId, status);
                int start = (int) pageable.getOffset();
                int end = Math.min((start + pageable.getPageSize()), statusTransactions.size());
                List<BalanceTransactionEntity> pageContent = statusTransactions.subList(start, end);
                return new org.springframework.data.domain.PageImpl<>(pageContent, pageable, statusTransactions.size());
            } else if (dateFrom != null && dateTo != null) {
                return transactionRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(dateFrom, dateTo, pageable);
            } else {
                return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
            }

        } catch (Exception e) {
            log.error("Ошибка при получении транзакций пользователя {}: {}", userId, e.getMessage(), e);
            // Возвращаем пустую страницу в случае ошибки
            return Page.empty();
        }
    }

    /**
     * Получение неподтвержденных транзакций
     * 
     * @return список pending транзакций
     */
    @Transactional(readOnly = true)
    public List<BalanceTransactionEntity> getPendingTransactions() {
        log.debug("Получение неподтвержденных транзакций");

        try {
            return transactionRepository.findByStatusOrderByCreatedAtDesc(TransactionStatus.PENDING);
        } catch (Exception e) {
            log.error("Ошибка при получении неподтвержденных транзакций: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Получение зависших транзакций (старые pending)
     * 
     * @param olderThan время, до которого считать транзакции зависшими
     * @return список зависших транзакций
     */
    @Transactional(readOnly = true)
    public List<BalanceTransactionEntity> getStaleTransactions(LocalDateTime olderThan) {
        log.debug("Получение зависших транзакций старше: {}", olderThan);

        try {
            return transactionRepository.findStaleTransactions(olderThan);
        } catch (Exception e) {
            log.error("Ошибка при получении зависших транзакций: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Отмена транзакции
     * 
     * @param transactionId ID транзакции
     * @param reason        причина отмены
     * @return true если транзакция отменена
     */
    @Transactional
    public boolean cancelTransaction(String transactionId, String reason) {
        log.info("Отмена транзакции {}: причина={}", transactionId, reason);

        try {
            Optional<BalanceTransactionEntity> transactionOpt = transactionRepository
                    .findByTransactionId(transactionId);

            if (transactionOpt.isPresent()) {
                BalanceTransactionEntity transaction = transactionOpt.get();

                // Проверяем, можно ли отменить транзакцию
                if (transaction.getStatus() == TransactionStatus.PENDING) {
                    transaction.cancel();
                    // Добавляем причину отмены в описание
                    String newDescription = transaction.getDescription() + " [ОТМЕНЕНО: " + reason + "]";
                    transaction.setDescription(newDescription);

                    transactionRepository.save(transaction);

                    log.info("Транзакция {} отменена: {}", transactionId, reason);
                    return true;
                } else {
                    log.warn("Невозможно отменить транзакцию {} со статусом {}",
                            transactionId, transaction.getStatus());
                    return false;
                }
            } else {
                log.warn("Транзакция для отмены не найдена: {}", transactionId);
                return false;
            }

        } catch (Exception e) {
            log.error("Ошибка при отмене транзакции {}: {}", transactionId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Автоматическая отмена зависших транзакций
     * 
     * @param olderThan время, до которого отменять транзакции
     * @return количество отмененных транзакций
     */
    @Transactional
    public int cancelStaleTransactions(LocalDateTime olderThan) {
        log.info("Автоматическая отмена зависших транзакций старше: {}", olderThan);

        try {
            LocalDateTime now = LocalDateTime.now();
            int cancelledCount = transactionRepository.cancelStaleTransactions(olderThan, now);

            log.info("Автоматически отменено {} зависших транзакций", cancelledCount);
            return cancelledCount;

        } catch (Exception e) {
            log.error("Ошибка при автоматической отмене зависших транзакций: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Получение статистики транзакций
     * 
     * @param dateFrom начальная дата
     * @param dateTo   конечная дата
     * @return статистика транзакций
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTransactionStatistics(LocalDateTime dateFrom, LocalDateTime dateTo) {
        log.debug("Получение статистики транзакций за период: {} - {}", dateFrom, dateTo);

        try {
            // Общая статистика
            List<Object[]> generalStats = transactionRepository.getTransactionStatistics();

            // Статистика по типам
            List<Object[]> typeStats = transactionRepository.getTransactionTypeStatistics();

            // Статистика по способам платежа
            List<Object[]> paymentStats = transactionRepository.getPaymentMethodStatistics();

            // Дневная активность
            List<Object[]> dailyActivity = transactionRepository.getDailyTransactionActivity(dateFrom);

            // Транзакции за период
            List<BalanceTransactionEntity> periodTransactions = transactionRepository
                    .findTransactionsBetweenDates(dateFrom, dateTo);

            // Дашборд сводка
            List<Object[]> dashboardSummary = transactionRepository.getTransactionDashboardSummary(dateFrom);

            Map<String, Object> statistics = Map.of(
                    "generalStatistics", generalStats,
                    "typeStatistics", typeStats,
                    "paymentMethodStatistics", paymentStats,
                    "dailyActivity", dailyActivity,
                    "periodTransactionCount", periodTransactions.size(),
                    "dashboardSummary", dashboardSummary);

            log.debug("Статистика транзакций за период: {} транзакций", periodTransactions.size());
            return statistics;

        } catch (Exception e) {
            log.error("Ошибка при получении статистики транзакций: {}", e.getMessage(), e);
            return Map.of();
        }
    }

    /**
     * Получение топ транзакций по сумме
     * 
     * @param limit количество записей
     * @return список топ транзакций
     */
    @Transactional(readOnly = true)
    public List<BalanceTransactionEntity> getTopTransactionsByAmount(int limit) {
        log.debug("Получение топ {} транзакций по сумме", limit);

        try {
            Pageable pageable = PageRequest.of(0, limit);
            return transactionRepository.findTopTransactionsByAmount(pageable);
        } catch (Exception e) {
            log.error("Ошибка при получении топ транзакций: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Получение транзакций по заказу
     * 
     * @param orderId ID заказа
     * @return список транзакций для заказа
     */
    @Transactional(readOnly = true)
    public List<BalanceTransactionEntity> getTransactionsByOrder(String orderId) {
        log.debug("Получение транзакций для заказа: {}", orderId);

        try {
            return transactionRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
        } catch (Exception e) {
            log.error("Ошибка при получении транзакций для заказа {}: {}", orderId, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Проверка существования транзакций для заказа
     * 
     * @param orderId ID заказа
     * @return true если есть транзакции для заказа
     */
    @Transactional(readOnly = true)
    public boolean hasTransactionsForOrder(String orderId) {
        log.debug("Проверка наличия транзакций для заказа: {}", orderId);

        try {
            return transactionRepository.existsByOrderId(orderId);
        } catch (Exception e) {
            log.error("Ошибка при проверке транзакций для заказа {}: {}", orderId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Получение статистики пользователя
     * 
     * @param userId ID пользователя
     * @return статистика пользователя
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getUserStatistics(Long userId) {
        log.debug("Получение статистики пользователя: {}", userId);

        try {
            List<Object[]> userStats = transactionRepository.getUserTransactionStatistics(userId);
            Long totalTransactions = transactionRepository.countByUserId(userId);
            Long completedTransactions = transactionRepository.countByUserIdAndStatus(userId,
                    TransactionStatus.COMPLETED);
            Long pendingTransactions = transactionRepository.countByUserIdAndStatus(userId, TransactionStatus.PENDING);
            Long failedTransactions = transactionRepository.countByUserIdAndStatus(userId, TransactionStatus.FAILED);

            Map<String, Object> statistics = Map.of(
                    "totalTransactions", totalTransactions,
                    "completedTransactions", completedTransactions,
                    "pendingTransactions", pendingTransactions,
                    "failedTransactions", failedTransactions,
                    "transactionsByType", userStats);

            log.debug("Статистика пользователя {}: всего транзакций={}, завершенных={}",
                    userId, totalTransactions, completedTransactions);

            return statistics;

        } catch (Exception e) {
            log.error("Ошибка при получении статистики пользователя {}: {}", userId, e.getMessage(), e);
            return Map.of();
        }
    }

    /**
     * Поиск транзакций с несоответствиями
     * 
     * @return список проблемных транзакций
     */
    @Transactional(readOnly = true)
    public List<BalanceTransactionEntity> findProblematicTransactions() {
        log.debug("Поиск транзакций с несоответствиями");

        try {
            return transactionRepository.findTransactionsWithBalanceDiscrepancies();
        } catch (Exception e) {
            log.error("Ошибка при поиске проблемных транзакций: {}", e.getMessage(), e);
            return List.of();
        }
    }
}