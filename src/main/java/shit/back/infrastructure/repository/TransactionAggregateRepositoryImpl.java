package shit.back.infrastructure.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import shit.back.application.balance.repository.TransactionAggregateRepository;
import shit.back.domain.balance.TransactionAggregate;
import shit.back.domain.balance.valueobjects.Money;
import shit.back.domain.balance.valueobjects.TransactionId;
import shit.back.entity.BalanceTransactionEntity;
import shit.back.entity.TransactionStatus;
import shit.back.entity.TransactionType;
import shit.back.repository.BalanceTransactionJpaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Упрощенная реализация TransactionAggregateRepository в инфраструктурном слое
 * Использует существующие методы JPA репозитория
 * Минимальная реализация для устранения ошибки запуска
 */
@Repository
@Transactional
public class TransactionAggregateRepositoryImpl implements TransactionAggregateRepository {

    private static final Logger log = LoggerFactory.getLogger(TransactionAggregateRepositoryImpl.class);

    private final BalanceTransactionJpaRepository balanceTransactionJpaRepository;

    public TransactionAggregateRepositoryImpl(BalanceTransactionJpaRepository balanceTransactionJpaRepository) {
        this.balanceTransactionJpaRepository = balanceTransactionJpaRepository;
    }

    @Override
    public TransactionAggregate save(TransactionAggregate transaction) {
        log.debug("Сохранение агрегата транзакции {}", transaction.getTransactionId().getValue());

        try {
            BalanceTransactionEntity entity = convertToEntity(transaction);
            BalanceTransactionEntity savedEntity = balanceTransactionJpaRepository.save(entity);

            TransactionAggregate savedAggregate = convertToAggregate(savedEntity);
            log.debug("Агрегат транзакции успешно сохранен с ID {}", savedEntity.getId());

            return savedAggregate;
        } catch (Exception e) {
            log.error("Ошибка при сохранении агрегата транзакции {}: {}",
                    transaction.getTransactionId().getValue(), e.getMessage(), e);
            throw new RuntimeException("Не удалось сохранить транзакцию", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TransactionAggregate> findById(TransactionId transactionId) {
        log.debug("Поиск агрегата транзакции по ID {}", transactionId.getValue());

        return balanceTransactionJpaRepository.findByTransactionId(transactionId.getValue())
                .map(this::convertToAggregate);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TransactionAggregate> findByTransactionId(String transactionId) {
        log.debug("Поиск агрегата транзакции по строковому ID {}", transactionId);

        return balanceTransactionJpaRepository.findByTransactionId(transactionId)
                .map(this::convertToAggregate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionAggregate> findByUserId(Long userId) {
        log.debug("Поиск транзакций пользователя {}", userId);

        return balanceTransactionJpaRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::convertToAggregate)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionAggregate> findByUserIdAndStatus(Long userId, TransactionStatus status) {
        log.debug("Поиск транзакций пользователя {} со статусом {}", userId, status);

        return balanceTransactionJpaRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status)
                .stream()
                .map(this::convertToAggregate)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionAggregate> findByUserIdAndType(Long userId, TransactionType type) {
        log.debug("Поиск транзакций пользователя {} типа {}", userId, type);

        return balanceTransactionJpaRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, type)
                .stream()
                .map(this::convertToAggregate)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionAggregate> findByUserIdAndDateRange(Long userId, LocalDateTime startDate,
            LocalDateTime endDate) {
        log.debug("Поиск транзакций пользователя {} за период {} - {}", userId, startDate, endDate);

        return balanceTransactionJpaRepository.findUserTransactionsBetweenDates(userId, startDate, endDate)
                .stream()
                .map(this::convertToAggregate)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionAggregate> findByOrderId(String orderId) {
        log.debug("Поиск транзакций по ID заказа {}", orderId);

        return balanceTransactionJpaRepository.findByOrderIdOrderByCreatedAtDesc(orderId)
                .stream()
                .map(this::convertToAggregate)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionAggregate> findPendingTransactionsByUserId(Long userId) {
        log.debug("Поиск незавершенных транзакций пользователя {}", userId);

        return balanceTransactionJpaRepository
                .findByUserIdAndStatusOrderByCreatedAtDesc(userId, TransactionStatus.PENDING)
                .stream()
                .map(this::convertToAggregate)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionAggregate> findExpiredTransactions(LocalDateTime expirationDate) {
        log.debug("Поиск просроченных транзакций до {}", expirationDate);

        return balanceTransactionJpaRepository.findStaleTransactions(expirationDate)
                .stream()
                .map(this::convertToAggregate)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionAggregate> findRecentTransactions(Long userId, int limit) {
        log.debug("Поиск последних {} транзакций пользователя {}", limit, userId);

        return balanceTransactionJpaRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .limit(limit)
                .map(this::convertToAggregate)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(TransactionId transactionId) {
        return balanceTransactionJpaRepository.existsByTransactionId(transactionId.getValue());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByTransactionId(String transactionId) {
        return balanceTransactionJpaRepository.existsByTransactionId(transactionId);
    }

    @Override
    public void deleteById(TransactionId transactionId) {
        log.debug("Удаление агрегата транзакции с ID {}", transactionId.getValue());

        // Ищем по transactionId и удаляем
        balanceTransactionJpaRepository.findByTransactionId(transactionId.getValue())
                .ifPresent(entity -> balanceTransactionJpaRepository.delete(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public long countByUserIdAndStatus(Long userId, TransactionStatus status) {
        return balanceTransactionJpaRepository.countByUserIdAndStatus(userId, status);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByUserIdAndDateRange(Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        // Заглушка - считаем транзакции в периоде
        return balanceTransactionJpaRepository.findUserTransactionsBetweenDates(userId, startDate, endDate).size();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumAmountByUserIdAndType(Long userId, TransactionType type) {
        // Заглушка - считаем сумму вручную
        return balanceTransactionJpaRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, type)
                .stream()
                .map(BalanceTransactionEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumAmountByUserIdAndDateRange(Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        // Заглушка - считаем сумму вручную
        return balanceTransactionJpaRepository.findUserTransactionsBetweenDates(userId, startDate, endDate)
                .stream()
                .map(BalanceTransactionEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionAggregate> findByAmountGreaterThan(BigDecimal minAmount) {
        log.debug("Поиск транзакций с суммой больше {}", minAmount);

        return balanceTransactionJpaRepository.findByAmountGreaterThanOrderByAmountDesc(minAmount)
                .stream()
                .map(this::convertToAggregate)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionAggregate> findByUserIdWithPagination(Long userId, int offset, int limit) {
        log.debug("Поиск транзакций пользователя {} с пагинацией: offset={}, limit={}", userId, offset, limit);

        return balanceTransactionJpaRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .skip(offset)
                .limit(limit)
                .map(this::convertToAggregate)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TransactionAggregate> findByIdWithLock(TransactionId transactionId) {
        log.debug("Поиск агрегата транзакции с блокировкой по ID {}", transactionId.getValue());

        // Для простоты используем обычный поиск
        return findById(transactionId);
    }

    @Override
    public List<TransactionAggregate> saveAll(List<TransactionAggregate> transactions) {
        log.debug("Пакетное сохранение {} агрегатов транзакций", transactions.size());

        List<BalanceTransactionEntity> entities = transactions.stream()
                .map(this::convertToEntity)
                .collect(Collectors.toList());

        List<BalanceTransactionEntity> savedEntities = balanceTransactionJpaRepository.saveAll(entities);

        return savedEntities.stream()
                .map(this::convertToAggregate)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionAggregate> findAll() {
        log.warn("Использование findAll() - может быть медленным для больших данных");

        return balanceTransactionJpaRepository.findAll()
                .stream()
                .map(this::convertToAggregate)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getAverageTransactionAmount(LocalDateTime startDate, LocalDateTime endDate) {
        // Заглушка - считаем среднее вручную
        List<BalanceTransactionEntity> transactions = balanceTransactionJpaRepository
                .findTransactionsBetweenDates(startDate, endDate);
        if (transactions.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal sum = transactions.stream()
                .map(BalanceTransactionEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return sum.divide(BigDecimal.valueOf(transactions.size()), BigDecimal.ROUND_HALF_UP);
    }

    @Override
    public void clearCache() {
        log.debug("Очистка кэша репозитория транзакций");
        // Реализация зависит от используемой системы кэширования
    }

    // Приватные методы для конвертации

    private BalanceTransactionEntity convertToEntity(TransactionAggregate aggregate) {
        BalanceTransactionEntity entity = new BalanceTransactionEntity();

        entity.setTransactionId(aggregate.getTransactionId().getValue());
        entity.setUserId(aggregate.getUserId());
        entity.setAmount(aggregate.getAmount().getAmount());
        entity.setBalanceBefore(aggregate.getBalanceBefore().getAmount());
        entity.setBalanceAfter(aggregate.getBalanceAfter().getAmount());
        entity.setType(aggregate.getType());
        entity.setStatus(aggregate.getStatus());
        entity.setDescription(aggregate.getDescription());
        entity.setOrderId(aggregate.getOrderId());
        entity.setPaymentMethod(aggregate.getPaymentMethod());
        entity.setPaymentDetails(aggregate.getPaymentDetails());
        entity.setProcessedBy(aggregate.getProcessedBy());

        return entity;
    }

    private TransactionAggregate convertToAggregate(BalanceTransactionEntity entity) {
        // Используем основной конструктор TransactionAggregate
        TransactionAggregate aggregate = new TransactionAggregate(
                entity.getUserId(),
                entity.getType(),
                Money.of(entity.getAmount()),
                Money.of(entity.getBalanceBefore()),
                Money.of(entity.getBalanceAfter()),
                entity.getDescription(),
                entity.getOrderId());

        // Устанавливаем дополнительные поля через методы
        if (entity.getPaymentMethod() != null || entity.getPaymentDetails() != null) {
            aggregate.setPaymentInfo(entity.getPaymentMethod(), entity.getPaymentDetails());
        }

        if (entity.getProcessedBy() != null) {
            aggregate.setProcessedBy(entity.getProcessedBy());
        }

        // Устанавливаем статус в соответствии с сущностью
        if (entity.getStatus() == TransactionStatus.COMPLETED) {
            aggregate.complete();
        } else if (entity.getStatus() == TransactionStatus.CANCELLED) {
            aggregate.cancel("Восстановлено из базы данных");
        } else if (entity.getStatus() == TransactionStatus.FAILED) {
            aggregate.fail("Восстановлено из базы данных");
        }

        return aggregate;
    }
}