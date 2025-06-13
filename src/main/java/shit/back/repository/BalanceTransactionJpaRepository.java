package shit.back.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import shit.back.entity.BalanceTransactionEntity;
import shit.back.entity.TransactionStatus;
import shit.back.entity.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JPA репозиторий для работы с транзакциями баланса
 * 
 * Предоставляет методы для поиска, фильтрации и анализа транзакций
 * с оптимизированными запросами для производительности и отчетности.
 */
@Repository
public interface BalanceTransactionJpaRepository extends JpaRepository<BalanceTransactionEntity, Long> {

        // === ОСНОВНЫЕ МЕТОДЫ ПОИСКА ===

        /**
         * Поиск транзакции по уникальному ID
         */
        Optional<BalanceTransactionEntity> findByTransactionId(String transactionId);

        /**
         * Проверка существования транзакции
         */
        boolean existsByTransactionId(String transactionId);

        /**
         * Транзакции конкретного пользователя
         */
        List<BalanceTransactionEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

        Page<BalanceTransactionEntity> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

        /**
         * Подсчет транзакций пользователя
         */
        Long countByUserId(Long userId);

        // === ПОИСК ПО ТИПУ ТРАНЗАКЦИИ ===

        /**
         * Транзакции по типу
         */
        List<BalanceTransactionEntity> findByTypeOrderByCreatedAtDesc(TransactionType type);

        Page<BalanceTransactionEntity> findByTypeOrderByCreatedAtDesc(TransactionType type, Pageable pageable);

        /**
         * Транзакции пользователя по типу
         */
        List<BalanceTransactionEntity> findByUserIdAndTypeOrderByCreatedAtDesc(Long userId, TransactionType type);

        Page<BalanceTransactionEntity> findByUserIdAndTypeOrderByCreatedAtDesc(Long userId, TransactionType type,
                        Pageable pageable);

        /**
         * Подсчет транзакций по типу
         */
        Long countByType(TransactionType type);

        Long countByUserIdAndType(Long userId, TransactionType type);

        // === ПОИСК ПО СТАТУСУ ===

        /**
         * Транзакции по статусу
         */
        List<BalanceTransactionEntity> findByStatusOrderByCreatedAtDesc(TransactionStatus status);

        Page<BalanceTransactionEntity> findByStatusOrderByCreatedAtDesc(TransactionStatus status, Pageable pageable);

        /**
         * Ожидающие транзакции пользователя
         */
        List<BalanceTransactionEntity> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, TransactionStatus status);

        /**
         * Подсчет транзакций по статусу
         */
        Long countByStatus(TransactionStatus status);

        Long countByUserIdAndStatus(Long userId, TransactionStatus status);

        // === ПОИСК ПО ЗАКАЗАМ ===

        /**
         * Транзакции связанные с заказом
         */
        List<BalanceTransactionEntity> findByOrderIdOrderByCreatedAtDesc(String orderId);

        /**
         * Транзакции без привязки к заказу
         */
        List<BalanceTransactionEntity> findByOrderIdIsNullOrderByCreatedAtDesc();

        /**
         * Проверка наличия транзакций для заказа
         */
        boolean existsByOrderId(String orderId);

        // === ПОИСК ПО СУММЕ ===

        /**
         * Транзакции больше указанной суммы
         */
        List<BalanceTransactionEntity> findByAmountGreaterThanOrderByAmountDesc(BigDecimal minAmount);

        Page<BalanceTransactionEntity> findByAmountGreaterThanOrderByAmountDesc(BigDecimal minAmount,
                        Pageable pageable);

        /**
         * Транзакции в диапазоне сумм
         */
        @Query("SELECT t FROM BalanceTransactionEntity t WHERE t.amount BETWEEN :minAmount AND :maxAmount ORDER BY t.amount DESC")
        List<BalanceTransactionEntity> findTransactionsInAmountRange(@Param("minAmount") BigDecimal minAmount,
                        @Param("maxAmount") BigDecimal maxAmount);

        // === ПОИСК ПО ВРЕМЕНИ ===

        /**
         * Транзакции за период
         */
        @Query("SELECT t FROM BalanceTransactionEntity t WHERE t.createdAt BETWEEN :startDate AND :endDate ORDER BY t.createdAt DESC")
        List<BalanceTransactionEntity> findTransactionsBetweenDates(@Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        Page<BalanceTransactionEntity> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime startDate,
                        LocalDateTime endDate, Pageable pageable);

        /**
         * Недавние транзакции
         */
        @Query("SELECT t FROM BalanceTransactionEntity t WHERE t.createdAt >= :since ORDER BY t.createdAt DESC")
        List<BalanceTransactionEntity> findRecentTransactions(@Param("since") LocalDateTime since);

        /**
         * Транзакции пользователя за период
         */
        @Query("SELECT t FROM BalanceTransactionEntity t WHERE t.userId = :userId AND t.createdAt BETWEEN :startDate AND :endDate ORDER BY t.createdAt DESC")
        List<BalanceTransactionEntity> findUserTransactionsBetweenDates(@Param("userId") Long userId,
                        @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

        // === ПОИСК ПО СПОСОБУ ПЛАТЕЖА ===

        /**
         * Транзакции по способу платежа
         */
        List<BalanceTransactionEntity> findByPaymentMethodOrderByCreatedAtDesc(String paymentMethod);

        /**
         * Статистика по способам платежа
         */
        @Query("SELECT t.paymentMethod, COUNT(t), SUM(t.amount) FROM BalanceTransactionEntity t WHERE t.paymentMethod IS NOT NULL GROUP BY t.paymentMethod ORDER BY COUNT(t) DESC")
        List<Object[]> getPaymentMethodStatistics();

        // === СТАТИСТИКА И АНАЛИТИКА ===

        /**
         * Общая статистика транзакций
         */
        @Query("SELECT COUNT(t), SUM(t.amount), AVG(t.amount), MAX(t.amount) FROM BalanceTransactionEntity t WHERE t.status = 'COMPLETED'")
        List<Object[]> getTransactionStatistics();

        /**
         * Статистика по типам транзакций
         */
        @Query("SELECT t.type, COUNT(t), SUM(t.amount), AVG(t.amount) FROM BalanceTransactionEntity t WHERE t.status = 'COMPLETED' GROUP BY t.type ORDER BY COUNT(t) DESC")
        List<Object[]> getTransactionTypeStatistics();

        /**
         * Статистика пользователя
         */
        @Query("SELECT t.type, COUNT(t), SUM(t.amount) FROM BalanceTransactionEntity t WHERE t.userId = :userId AND t.status = 'COMPLETED' GROUP BY t.type")
        List<Object[]> getUserTransactionStatistics(@Param("userId") Long userId);

        /**
         * Дневная активность транзакций
         */
        @Query("SELECT DATE(t.createdAt), COUNT(t), SUM(t.amount) FROM BalanceTransactionEntity t WHERE t.createdAt >= :fromDate GROUP BY DATE(t.createdAt) ORDER BY DATE(t.createdAt)")
        List<Object[]> getDailyTransactionActivity(@Param("fromDate") LocalDateTime fromDate);

        /**
         * Часовая активность транзакций (для анализа пиков)
         */
        @Query("SELECT EXTRACT(HOUR FROM t.createdAt), COUNT(t) FROM BalanceTransactionEntity t WHERE t.createdAt >= :fromDate GROUP BY EXTRACT(HOUR FROM t.createdAt) ORDER BY EXTRACT(HOUR FROM t.createdAt)")
        List<Object[]> getHourlyTransactionActivity(@Param("fromDate") LocalDateTime fromDate);

        // === ТОП ОПЕРАЦИИ ===

        /**
         * Топ транзакции по сумме
         */
        @Query("SELECT t FROM BalanceTransactionEntity t WHERE t.status = 'COMPLETED' ORDER BY t.amount DESC")
        List<BalanceTransactionEntity> findTopTransactionsByAmount(Pageable pageable);

        /**
         * Топ пользователи по объему транзакций
         */
        @Query("SELECT t.userId, COUNT(t), SUM(t.amount) FROM BalanceTransactionEntity t WHERE t.status = 'COMPLETED' GROUP BY t.userId ORDER BY SUM(t.amount) DESC")
        List<Object[]> findTopUsersByTransactionVolume(Pageable pageable);

        // === АДМИНИСТРАТИВНЫЕ ОПЕРАЦИИ ===

        /**
         * Обновление статуса транзакции
         */
        @Modifying
        @Query("UPDATE BalanceTransactionEntity t SET t.status = :status, t.completedAt = :completedAt WHERE t.transactionId = :transactionId")
        int updateTransactionStatus(@Param("transactionId") String transactionId,
                        @Param("status") TransactionStatus status,
                        @Param("completedAt") LocalDateTime completedAt);

        /**
         * Обновление обработчика транзакции
         */
        @Modifying
        @Query("UPDATE BalanceTransactionEntity t SET t.processedBy = :processedBy WHERE t.transactionId = :transactionId")
        int updateTransactionProcessor(@Param("transactionId") String transactionId,
                        @Param("processedBy") String processedBy);

        /**
         * Отмена зависших транзакций
         */
        @Modifying
        @Query("UPDATE BalanceTransactionEntity t SET t.status = 'CANCELLED', t.completedAt = :now WHERE t.status = 'PENDING' AND t.createdAt < :cutoff")
        int cancelStaleTransactions(@Param("cutoff") LocalDateTime cutoff, @Param("now") LocalDateTime now);

        // === ПОИСК ПРОБЛЕМНЫХ ТРАНЗАКЦИЙ ===

        /**
         * Зависшие транзакции (долго в статусе PENDING)
         */
        @Query("SELECT t FROM BalanceTransactionEntity t WHERE t.status = 'PENDING' AND t.createdAt < :cutoff ORDER BY t.createdAt ASC")
        List<BalanceTransactionEntity> findStaleTransactions(@Param("cutoff") LocalDateTime cutoff);

        /**
         * Неудачные транзакции за период
         */
        List<BalanceTransactionEntity> findByStatusAndCreatedAtAfterOrderByCreatedAtDesc(TransactionStatus status,
                        LocalDateTime since);

        /**
         * Транзакции с несоответствиями в балансе
         */
        @Query("SELECT t FROM BalanceTransactionEntity t WHERE ABS(t.balanceAfter - t.balanceBefore) != t.amount AND t.type != 'ADJUSTMENT'")
        List<BalanceTransactionEntity> findTransactionsWithBalanceDiscrepancies();

        // === ОТЧЕТЫ И ЭКСПОРТ ===

        /**
         * Транзакции для отчета за период
         */
        @Query("SELECT t FROM BalanceTransactionEntity t WHERE t.createdAt BETWEEN :startDate AND :endDate ORDER BY t.createdAt ASC")
        List<BalanceTransactionEntity> findTransactionsForReport(@Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        /**
         * Транзакции пользователя для экспорта
         */
        @Query("SELECT t FROM BalanceTransactionEntity t WHERE t.userId = :userId ORDER BY t.createdAt ASC")
        List<BalanceTransactionEntity> findUserTransactionsForExport(@Param("userId") Long userId);

        /**
         * Сводка транзакций для дашборда
         */
        @Query("SELECT " +
                        "COUNT(CASE WHEN t.status = 'COMPLETED' THEN 1 END), " +
                        "COUNT(CASE WHEN t.status = 'PENDING' THEN 1 END), " +
                        "COUNT(CASE WHEN t.status = 'FAILED' THEN 1 END), " +
                        "SUM(CASE WHEN t.status = 'COMPLETED' AND t.type = 'DEPOSIT' THEN t.amount ELSE 0 END), " +
                        "SUM(CASE WHEN t.status = 'COMPLETED' AND t.type = 'PURCHASE' THEN t.amount ELSE 0 END) " +
                        "FROM BalanceTransactionEntity t WHERE t.createdAt >= :fromDate")
        List<Object[]> getTransactionDashboardSummary(@Param("fromDate") LocalDateTime fromDate);

        /**
         * Средняя сумма транзакций по типу
         */
        @Query("SELECT t.type, AVG(t.amount), COUNT(t) FROM BalanceTransactionEntity t WHERE t.status = 'COMPLETED' AND t.createdAt >= :fromDate GROUP BY t.type")
        List<Object[]> getAverageTransactionAmountsByType(@Param("fromDate") LocalDateTime fromDate);
        // === ДОПОЛНИТЕЛЬНЫЕ МЕТОДЫ ДЛЯ ТЕСТИРОВАНИЯ ===

        /**
         * Удаление всех транзакций пользователя (для тестов)
         */
        @Modifying
        @Query("DELETE FROM BalanceTransactionEntity t WHERE t.userId = :userId")
        void deleteByUserId(@Param("userId") Long userId);

        /**
         * Поиск транзакций пользователя по типу и статусу
         */
        List<BalanceTransactionEntity> findByUserIdAndTypeAndStatus(Long userId, TransactionType type,
                        TransactionStatus status);

        /**
         * Подсчет транзакций пользователя по типу и статусу
         */
        Long countByUserIdAndTypeAndStatus(Long userId, TransactionType type, TransactionStatus status);

        /**
         * Поиск последних транзакций пользователя с лимитом
         */
        @Query("SELECT t FROM BalanceTransactionEntity t WHERE t.userId = :userId ORDER BY t.createdAt DESC LIMIT :limit")
        List<BalanceTransactionEntity> findTopByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId,
                        @Param("limit") int limit);
}