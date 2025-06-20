package shit.back.infrastructure.balance.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import shit.back.application.balance.repository.TransactionAggregateRepository;
import shit.back.domain.balance.TransactionAggregate;
import shit.back.domain.balance.exceptions.InvalidTransactionException;
import shit.back.domain.balance.valueobjects.TransactionId;
import shit.back.entity.TransactionStatus;
import shit.back.entity.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Реализация репозитория TransactionAggregate напрямую с JPA
 * 
 * ВАЖНО: TransactionAggregate также является JPA Entity (@Entity),
 * поэтому мы можем использовать его напрямую без маппинга.
 * 
 * Обеспечивает:
 * - Работу с TransactionAggregate как JPA Entity
 * - Batch operations для производительности
 * - Complex queries через JPQL
 * - Transaction management
 */
@Repository
@Transactional
public class TransactionAggregateRepositoryImpl implements TransactionAggregateRepository {

    private static final Logger log = LoggerFactory.getLogger(TransactionAggregateRepositoryImpl.class);

    @Autowired
    private TransactionAggregateJpaRepository transactionAggregateJpaRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<TransactionAggregate> findById(TransactionId transactionId) {
        log.debug("Поиск транзакции по ID: {}", transactionId.getValue());

        try {
            return transactionAggregateJpaRepository.findByTransactionId_Value(transactionId.getValue())
                    .map(transaction -> {
                        log.debug("Найдена транзакция: userId={}, amount={}",
                                transaction.getUserId(), transaction.getAmount().getAmount());
                        return transaction;
                    });
        } catch (DataAccessException e) {
            log.error("Ошибка при поиске транзакции ID {}: {}", transactionId.getValue(), e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionAggregate> findByUserId(Long userId) {
        log.debug("Поиск транзакций по userId: {}", userId);

        try {
            List<TransactionAggregate> transactions = transactionAggregateJpaRepository
                    .findByUserIdOrderByCreatedAtDesc(userId);
            log.debug("Найдено {} транзакций пользователя {}", transactions.size(), userId);
            return transactions;

        } catch (DataAccessException e) {
            log.error("Ошибка при поиске транзакций пользователя {}: {}", userId, e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionAggregate> findByUserIdWithPagination(Long userId, int page, int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            return transactionAggregateJpaRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        } catch (DataAccessException e) {
            log.error("Ошибка при поиске транзакций с пагинацией пользователя {}: {}", userId, e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    public TransactionAggregate save(TransactionAggregate transaction) {
        log.debug("Сохранение транзакции: userId={}, amount={}",
                transaction.getUserId(), transaction.getAmount().getAmount());

        try {
            TransactionAggregate savedTransaction = transactionAggregateJpaRepository.save(transaction);

            log.debug("Транзакция успешно сохранена: ID={}, userId={}",
                    savedTransaction.getTransactionId().getValue(), savedTransaction.getUserId());

            return savedTransaction;

        } catch (DataAccessException e) {
            log.error("Ошибка при сохранении транзакции userId {}: {}",
                    transaction.getUserId(), e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    public List<TransactionAggregate> saveAll(List<TransactionAggregate> transactions) {
        try {
            return transactionAggregateJpaRepository.saveAll(transactions);
        } catch (DataAccessException e) {
            log.error("Ошибка при массовом сохранении транзакций: {}", e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Transactional(readOnly = true)
    public List<TransactionAggregate> findByStatus(TransactionStatus status) {
        try {
            return transactionAggregateJpaRepository.findByStatusOrderByCreatedAtDesc(status);
        } catch (DataAccessException e) {
            log.error("Ошибка при поиске транзакций по статусу {}: {}", status, e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Transactional(readOnly = true)
    public List<TransactionAggregate> findByType(TransactionType type) {
        try {
            return transactionAggregateJpaRepository.findByTypeOrderByCreatedAtDesc(type);
        } catch (DataAccessException e) {
            log.error("Ошибка при поиске транзакций по типу {}: {}", type, e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Transactional(readOnly = true)
    public List<TransactionAggregate> findByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        try {
            return transactionAggregateJpaRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(startDate, endDate);
        } catch (DataAccessException e) {
            log.error("Ошибка при поиске транзакций в диапазоне дат: {}", e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Transactional(readOnly = true)
    public List<TransactionAggregate> findByAmountRange(BigDecimal minAmount, BigDecimal maxAmount) {
        try {
            return transactionAggregateJpaRepository.findByAmount_AmountBetweenOrderByAmountDesc(minAmount, maxAmount);
        } catch (DataAccessException e) {
            log.error("Ошибка при поиске транзакций в диапазоне сумм: {}", e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Transactional(readOnly = true)
    public long countByUserId(Long userId) {
        try {
            return transactionAggregateJpaRepository.countByUserId(userId);
        } catch (DataAccessException e) {
            log.error("Ошибка при подсчете транзакций пользователя {}: {}", userId, e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Transactional(readOnly = true)
    public long countByStatus(TransactionStatus status) {
        try {
            return transactionAggregateJpaRepository.countByStatus(status);
        } catch (DataAccessException e) {
            log.error("Ошибка при подсчете транзакций по статусу {}: {}", status, e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Transactional(readOnly = true)
    public long countByType(TransactionType type) {
        try {
            return transactionAggregateJpaRepository.countByType(type);
        } catch (DataAccessException e) {
            log.error("Ошибка при подсчете транзакций по типу {}: {}", type, e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Transactional(readOnly = true)
    public List<TransactionAggregate> findPendingTransactions() {
        try {
            return transactionAggregateJpaRepository.findByStatusOrderByCreatedAtDesc(TransactionStatus.PENDING);
        } catch (DataAccessException e) {
            log.error("Ошибка при поиске ожидающих транзакций: {}", e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Transactional(readOnly = true)
    public List<TransactionAggregate> findRecentTransactions(LocalDateTime since) {
        try {
            return transactionAggregateJpaRepository.findByCreatedAtAfterOrderByCreatedAtDesc(since);
        } catch (DataAccessException e) {
            log.error("Ошибка при поиске недавних транзакций: {}", e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Transactional(readOnly = true)
    public List<TransactionAggregate> findTopTransactionsByAmount(int limit) {
        try {
            Pageable pageable = PageRequest.of(0, limit);
            return transactionAggregateJpaRepository.findTopByOrderByAmount_AmountDesc(pageable);
        } catch (DataAccessException e) {
            log.error("Ошибка при поиске топ транзакций по сумме: {}", e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(TransactionId transactionId) {
        try {
            return transactionAggregateJpaRepository.existsByTransactionId_Value(transactionId.getValue());
        } catch (DataAccessException e) {
            log.error("Ошибка при проверке существования транзакции ID {}: {}",
                    transactionId.getValue(), e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    public void deleteById(TransactionId transactionId) {
        try {
            transactionAggregateJpaRepository.deleteByTransactionId_Value(transactionId.getValue());
        } catch (DataAccessException e) {
            log.error("Ошибка при удалении транзакции ID {}: {}", transactionId.getValue(), e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionAggregate> findAll() {
        try {
            return transactionAggregateJpaRepository.findAll();
        } catch (DataAccessException e) {
            log.error("Ошибка при поиске всех транзакций: {}", e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Transactional(readOnly = true)
    public List<TransactionAggregate> findWithPagination(int page, int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            return transactionAggregateJpaRepository.findAllByOrderByCreatedAtDesc(pageable);
        } catch (DataAccessException e) {
            log.error("Ошибка при поиске транзакций с пагинацией: {}", e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    // Недостающие методы из интерфейса

    @Override
    @Transactional(readOnly = true)
    public Optional<TransactionAggregate> findByTransactionId(String transactionId) {
        try {
            return transactionAggregateJpaRepository.findByTransactionId_Value(transactionId);
        } catch (DataAccessException e) {
            log.error("Ошибка при поиске транзакции по transactionId {}: {}", transactionId, e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionAggregate> findByUserIdAndStatus(Long userId, TransactionStatus status) {
        try {
            return transactionAggregateJpaRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status);
        } catch (DataAccessException e) {
            log.error("Ошибка при поиске транзакций пользователя {} по статусу {}: {}", userId, status, e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionAggregate> findByUserIdAndType(Long userId, TransactionType type) {
        try {
            return transactionAggregateJpaRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, type);
        } catch (DataAccessException e) {
            log.error("Ошибка при поиске транзакций пользователя {} по типу {}: {}", userId, type, e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionAggregate> findByUserIdAndDateRange(Long userId, LocalDateTime startDate,
            LocalDateTime endDate) {
        try {
            return transactionAggregateJpaRepository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(userId,
                    startDate, endDate);
        } catch (DataAccessException e) {
            log.error("Ошибка при поиске транзакций пользователя {} в диапазоне дат: {}", userId, e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public long countByUserIdAndStatus(Long userId, TransactionStatus status) {
        try {
            return transactionAggregateJpaRepository.countByUserIdAndStatus(userId, status);
        } catch (DataAccessException e) {
            log.error("Ошибка при подсчете транзакций пользователя {} по статусу {}: {}", userId, status,
                    e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public long countByUserIdAndDateRange(Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        try {
            return transactionAggregateJpaRepository.countByUserIdAndCreatedAtBetween(userId, startDate, endDate);
        } catch (DataAccessException e) {
            log.error("Ошибка при подсчете транзакций пользователя {} в диапазоне дат: {}", userId, e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionAggregate> findByOrderId(String orderId) {
        try {
            return transactionAggregateJpaRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
        } catch (DataAccessException e) {
            log.error("Ошибка при поиске транзакций по orderId {}: {}", orderId, e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionAggregate> findByAmountGreaterThan(BigDecimal amount) {
        try {
            return transactionAggregateJpaRepository.findByAmount_AmountGreaterThanOrderByAmountDesc(amount);
        } catch (DataAccessException e) {
            log.error("Ошибка при поиске транзакций больше суммы {}: {}", amount, e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionAggregate> findExpiredTransactions(LocalDateTime cutoffDate) {
        try {
            return transactionAggregateJpaRepository.findExpiredPendingTransactions(cutoffDate);
        } catch (DataAccessException e) {
            log.error("Ошибка при поиске истекших транзакций: {}", e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionAggregate> findPendingTransactionsByUserId(Long userId) {
        try {
            return transactionAggregateJpaRepository.findPendingTransactionsByUserId(userId);
        } catch (DataAccessException e) {
            log.error("Ошибка при поиске ожидающих транзакций пользователя {}: {}", userId, e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionAggregate> findRecentTransactions(Long userId, int limit) {
        try {
            return transactionAggregateJpaRepository.findTopByUserIdOrderByCreatedAtDesc(userId, limit);
        } catch (DataAccessException e) {
            log.error("Ошибка при поиске недавних транзакций пользователя {}: {}", userId, e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TransactionAggregate> findByIdWithLock(TransactionId transactionId) {
        try {
            return transactionAggregateJpaRepository.findByTransactionIdWithLock(transactionId.getValue());
        } catch (DataAccessException e) {
            log.error("Ошибка при поиске транзакции с блокировкой ID {}: {}", transactionId.getValue(), e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumAmountByUserIdAndType(Long userId, TransactionType type) {
        try {
            return transactionAggregateJpaRepository.sumAmountByUserIdAndType(userId, type);
        } catch (DataAccessException e) {
            log.error("Ошибка при суммировании транзакций пользователя {} по типу {}: {}", userId, type,
                    e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumAmountByUserIdAndDateRange(Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        try {
            return transactionAggregateJpaRepository.sumAmountByUserIdAndDateRange(userId, startDate, endDate);
        } catch (DataAccessException e) {
            log.error("Ошибка при суммировании транзакций пользователя {} в диапазоне дат: {}", userId, e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getAverageTransactionAmount(LocalDateTime startDate, LocalDateTime endDate) {
        try {
            return transactionAggregateJpaRepository.getAverageTransactionAmount(startDate, endDate);
        } catch (DataAccessException e) {
            log.error("Ошибка при вычислении средней суммы транзакций: {}", e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByTransactionId(String transactionId) {
        try {
            return transactionAggregateJpaRepository.existsByTransactionId_Value(transactionId);
        } catch (DataAccessException e) {
            log.error("Ошибка при проверке существования транзакции {}: {}", transactionId, e.getMessage());
            throw new InvalidTransactionException("DATA_ACCESS_ERROR", e.getMessage(), "Валидные данные");
        }
    }

    @Override
    public void clearCache() {
        // В данной реализации кэш не используется
        log.debug("Очистка кэша (no-op в данной реализации)");
    }
}