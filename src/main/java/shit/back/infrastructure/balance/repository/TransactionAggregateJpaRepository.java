package shit.back.infrastructure.balance.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import shit.back.domain.balance.TransactionAggregate;
import shit.back.entity.TransactionStatus;
import shit.back.entity.TransactionType;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JPA Repository для TransactionAggregate
 * 
 * Обеспечивает базовые CRUD операции и специализированные запросы
 * для работы с доменным агрегатом TransactionAggregate
 */
@Repository
public interface TransactionAggregateJpaRepository extends JpaRepository<TransactionAggregate, Long> {

    /**
     * Поиск транзакции по уникальному transactionId
     */
    Optional<TransactionAggregate> findByTransactionId_Value(String transactionId);

    /**
     * Проверка существования транзакции по transactionId
     */
    boolean existsByTransactionId_Value(String transactionId);

    /**
     * Удаление транзакции по transactionId
     */
    void deleteByTransactionId_Value(String transactionId);

    /**
     * Поиск транзакций пользователя
     */
    List<TransactionAggregate> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Поиск транзакций пользователя с пагинацией
     */
    List<TransactionAggregate> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Подсчет транзакций пользователя
     */
    long countByUserId(Long userId);

    /**
     * Поиск по статусу
     */
    List<TransactionAggregate> findByStatusOrderByCreatedAtDesc(TransactionStatus status);

    /**
     * Подсчет по статусу
     */
    long countByStatus(TransactionStatus status);

    /**
     * Поиск транзакций пользователя по статусу
     */
    List<TransactionAggregate> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, TransactionStatus status);

    /**
     * Подсчет транзакций пользователя по статусу
     */
    long countByUserIdAndStatus(Long userId, TransactionStatus status);

    /**
     * Поиск по типу транзакции
     */
    List<TransactionAggregate> findByTypeOrderByCreatedAtDesc(TransactionType type);

    /**
     * Подсчет по типу
     */
    long countByType(TransactionType type);

    /**
     * Поиск транзакций пользователя по типу
     */
    List<TransactionAggregate> findByUserIdAndTypeOrderByCreatedAtDesc(Long userId, TransactionType type);

    /**
     * Поиск по диапазону дат
     */
    List<TransactionAggregate> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime startDate,
            LocalDateTime endDate);

    /**
     * Поиск транзакций пользователя по диапазону дат
     */
    List<TransactionAggregate> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long userId, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Подсчет транзакций пользователя по диапазону дат
     */
    long countByUserIdAndCreatedAtBetween(Long userId, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Поиск по диапазону сумм
     */
    List<TransactionAggregate> findByAmount_AmountBetweenOrderByAmountDesc(BigDecimal minAmount, BigDecimal maxAmount);

    /**
     * Поиск транзакций больше указанной суммы
     */
    List<TransactionAggregate> findByAmount_AmountGreaterThanOrderByAmountDesc(BigDecimal amount);

    /**
     * Поиск недавних транзакций
     */
    List<TransactionAggregate> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime since);

    /**
     * Поиск недавних транзакций пользователя с лимитом
     */
    @Query("SELECT t FROM TransactionAggregate t WHERE t.userId = :userId ORDER BY t.createdAt DESC LIMIT :limit")
    List<TransactionAggregate> findTopByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId,
            @Param("limit") int limit);

    /**
     * Топ транзакции по сумме
     */
    @Query("SELECT t FROM TransactionAggregate t ORDER BY t.amount.amount DESC")
    List<TransactionAggregate> findTopByOrderByAmount_AmountDesc(Pageable pageable);

    /**
     * Все транзакции с сортировкой по дате
     */
    @Query("SELECT t FROM TransactionAggregate t ORDER BY t.createdAt DESC")
    List<TransactionAggregate> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Поиск по orderId
     */
    List<TransactionAggregate> findByOrderIdOrderByCreatedAtDesc(String orderId);

    /**
     * Поиск истекших транзакций (старше указанной даты в статусе PENDING)
     */
    @Query("SELECT t FROM TransactionAggregate t WHERE t.status = 'PENDING' AND t.createdAt < :cutoffDate ORDER BY t.createdAt ASC")
    List<TransactionAggregate> findExpiredPendingTransactions(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Поиск ожидающих транзакций пользователя
     */
    @Query("SELECT t FROM TransactionAggregate t WHERE t.userId = :userId AND t.status = 'PENDING' ORDER BY t.createdAt DESC")
    List<TransactionAggregate> findPendingTransactionsByUserId(@Param("userId") Long userId);

    /**
     * Поиск транзакции с блокировкой
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TransactionAggregate t WHERE t.transactionId.value = :transactionId")
    Optional<TransactionAggregate> findByTransactionIdWithLock(@Param("transactionId") String transactionId);

    /**
     * Сумма транзакций пользователя по типу
     */
    @Query("SELECT COALESCE(SUM(t.amount.amount), 0) FROM TransactionAggregate t WHERE t.userId = :userId AND t.type = :type AND t.status = 'COMPLETED'")
    BigDecimal sumAmountByUserIdAndType(@Param("userId") Long userId, @Param("type") TransactionType type);

    /**
     * Сумма транзакций пользователя по диапазону дат
     */
    @Query("SELECT COALESCE(SUM(t.amount.amount), 0) FROM TransactionAggregate t WHERE t.userId = :userId AND t.status = 'COMPLETED' AND t.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal sumAmountByUserIdAndDateRange(@Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Средняя сумма транзакции за период
     */
    @Query("SELECT COALESCE(AVG(t.amount.amount), 0) FROM TransactionAggregate t WHERE t.status = 'COMPLETED' AND t.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal getAverageTransactionAmount(@Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Статистические запросы
     */
    @Query("SELECT COUNT(t), SUM(t.amount.amount), AVG(t.amount.amount) FROM TransactionAggregate t WHERE t.status = 'COMPLETED'")
    List<Object[]> getTransactionStatistics();

    /**
     * Статистика по типам транзакций
     */
    @Query("SELECT t.type, COUNT(t), SUM(t.amount.amount), AVG(t.amount.amount) FROM TransactionAggregate t WHERE t.status = 'COMPLETED' GROUP BY t.type ORDER BY COUNT(t) DESC")
    List<Object[]> getTransactionTypeStatistics();

    /**
     * Статистика пользователя
     */
    @Query("SELECT t.type, COUNT(t), SUM(t.amount.amount) FROM TransactionAggregate t WHERE t.userId = :userId AND t.status = 'COMPLETED' GROUP BY t.type")
    List<Object[]> getUserTransactionStatistics(@Param("userId") Long userId);

    /**
     * Дневная активность транзакций
     */
    @Query("SELECT DATE(t.createdAt), COUNT(t), SUM(t.amount.amount) FROM TransactionAggregate t WHERE t.createdAt >= :fromDate GROUP BY DATE(t.createdAt) ORDER BY DATE(t.createdAt)")
    List<Object[]> getDailyTransactionActivity(@Param("fromDate") LocalDateTime fromDate);
}