package shit.back.infrastructure.balance.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import shit.back.domain.balance.BalanceAggregate;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * JPA Repository для BalanceAggregate
 * 
 * Обеспечивает базовые CRUD операции и специализированные запросы
 * для работы с доменным агрегатом BalanceAggregate
 */
@Repository
public interface BalanceAggregateJpaRepository extends JpaRepository<BalanceAggregate, Long> {

    /**
     * Поиск баланса по ID пользователя
     */
    Optional<BalanceAggregate> findByUserId(Long userId);

    /**
     * Проверка существования баланса для пользователя
     */
    boolean existsByUserId(Long userId);

    /**
     * Поиск активного баланса пользователя
     */
    Optional<BalanceAggregate> findByUserIdAndIsActiveTrue(Long userId);

    /**
     * Поиск баланса с блокировкой
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BalanceAggregate b WHERE b.id = :id")
    Optional<BalanceAggregate> findByIdWithLock(@Param("id") Long id);

    /**
     * Подсчет активных балансов
     */
    long countByIsActiveTrue();

    /**
     * Поиск балансов по валюте
     */
    List<BalanceAggregate> findByCurrency_Code(String currencyCode);

    /**
     * Подсчет балансов по валюте
     */
    long countByCurrency_Code(String currencyCode);

    /**
     * Поиск балансов больше указанной суммы с определенной валютой
     */
    List<BalanceAggregate> findByCurrentBalance_AmountGreaterThanAndCurrency_Code(
            BigDecimal amount, String currencyCode);

    /**
     * Поиск балансов с точной суммой
     */
    List<BalanceAggregate> findByCurrentBalance_Amount(BigDecimal amount);

    /**
     * Топ балансы по сумме
     */
    @Query("SELECT b FROM BalanceAggregate b WHERE b.isActive = true ORDER BY b.currentBalance.amount DESC")
    List<BalanceAggregate> findTopByOrderByCurrentBalance_AmountDesc(Pageable pageable);

    /**
     * Поиск всех балансов с сортировкой по дате обновления
     */
    @Query("SELECT b FROM BalanceAggregate b ORDER BY b.lastUpdated DESC")
    List<BalanceAggregate> findAllByOrderByLastUpdatedDesc(Pageable pageable);

    /**
     * Поиск активных балансов с сортировкой
     */
    List<BalanceAggregate> findByIsActiveTrueOrderByLastUpdatedDesc();

    /**
     * Поиск балансов с пагинацией
     */
    @Query("SELECT b FROM BalanceAggregate b WHERE b.isActive = true ORDER BY b.lastUpdated DESC")
    List<BalanceAggregate> findActiveBalancesWithPagination(Pageable pageable);

    /**
     * Статистические запросы
     */
    @Query("SELECT COUNT(b), SUM(b.currentBalance.amount), AVG(b.currentBalance.amount) FROM BalanceAggregate b WHERE b.isActive = true")
    List<Object[]> getBalanceStatistics();

    /**
     * Поиск балансов по диапазону сумм
     */
    @Query("SELECT b FROM BalanceAggregate b WHERE b.isActive = true AND b.currentBalance.amount BETWEEN :minAmount AND :maxAmount ORDER BY b.currentBalance.amount DESC")
    List<BalanceAggregate> findByAmountRange(@Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount);

    /**
     * Поиск VIP пользователей (высокий баланс или оборот)
     */
    @Query("SELECT b FROM BalanceAggregate b WHERE b.isActive = true AND " +
            "(b.currentBalance.amount >= :minBalance OR " +
            "(b.totalDeposited.amount + b.totalSpent.amount) >= :minTurnover) " +
            "ORDER BY b.currentBalance.amount DESC")
    List<BalanceAggregate> findVipUsers(@Param("minBalance") BigDecimal minBalance,
            @Param("minTurnover") BigDecimal minTurnover);

    /**
     * Административные операции
     */
    @Query("UPDATE BalanceAggregate b SET b.isActive = false WHERE b.userId = :userId")
    int deactivateByUserId(@Param("userId") Long userId);

    @Query("UPDATE BalanceAggregate b SET b.isActive = true WHERE b.userId = :userId")
    int activateByUserId(@Param("userId") Long userId);

    /**
     * Поиск проблемных балансов
     */
    @Query("SELECT b FROM BalanceAggregate b WHERE b.isActive = true AND " +
            "(b.currentBalance.amount < 0 OR b.totalDeposited.amount < 0 OR b.totalSpent.amount < 0)")
    List<BalanceAggregate> findProblematicBalances();

    /**
     * Поиск балансов с заметками
     */
    @Query("SELECT b FROM BalanceAggregate b WHERE b.notes IS NOT NULL AND LENGTH(b.notes) > 0 ORDER BY b.lastUpdated DESC")
    List<BalanceAggregate> findBalancesWithNotes();
}