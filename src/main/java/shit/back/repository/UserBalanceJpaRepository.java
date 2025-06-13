package shit.back.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import shit.back.entity.UserBalanceEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JPA репозиторий для работы с балансами пользователей
 * 
 * Предоставляет методы для поиска, обновления и анализа балансов пользователей
 * с оптимизированными запросами для производительности.
 */
@Repository
public interface UserBalanceJpaRepository extends JpaRepository<UserBalanceEntity, Long> {

    // === ОСНОВНЫЕ МЕТОДЫ ПОИСКА ===

    /**
     * Поиск баланса по ID пользователя
     */
    Optional<UserBalanceEntity> findByUserId(Long userId);

    /**
     * Проверка существования баланса для пользователя
     */
    boolean existsByUserId(Long userId);

    /**
     * Поиск активных балансов
     */
    List<UserBalanceEntity> findByIsActiveTrueOrderByLastUpdatedDesc();

    Page<UserBalanceEntity> findByIsActiveTrueOrderByLastUpdatedDesc(Pageable pageable);

    /**
     * Подсчет активных балансов
     */
    Long countByIsActiveTrue();

    // === ПОИСК ПО ВАЛЮТЕ ===

    /**
     * Поиск балансов по валюте
     */
    List<UserBalanceEntity> findByCurrency(String currency);

    Page<UserBalanceEntity> findByCurrencyAndIsActiveTrue(String currency, Pageable pageable);

    /**
     * Статистика по валютам
     */
    @Query("SELECT b.currency, COUNT(b), SUM(b.currentBalance) FROM UserBalanceEntity b WHERE b.isActive = true GROUP BY b.currency ORDER BY COUNT(b) DESC")
    List<Object[]> getCurrencyStatistics();

    // === ПОИСК ПО СУММЕ БАЛАНСА ===

    /**
     * Балансы больше указанной суммы
     */
    List<UserBalanceEntity> findByCurrentBalanceGreaterThanAndIsActiveTrueOrderByCurrentBalanceDesc(
            BigDecimal minBalance);

    Page<UserBalanceEntity> findByCurrentBalanceGreaterThanAndIsActiveTrueOrderByCurrentBalanceDesc(
            BigDecimal minBalance, Pageable pageable);

    /**
     * Балансы в определенном диапазоне
     */
    @Query("SELECT b FROM UserBalanceEntity b WHERE b.isActive = true AND b.currentBalance BETWEEN :minBalance AND :maxBalance ORDER BY b.currentBalance DESC")
    List<UserBalanceEntity> findBalancesInRange(@Param("minBalance") BigDecimal minBalance,
            @Param("maxBalance") BigDecimal maxBalance);

    /**
     * Пользователи с нулевым балансом
     */
    List<UserBalanceEntity> findByCurrentBalanceAndIsActiveTrue(BigDecimal balance);

    Long countByCurrentBalanceAndIsActiveTrue(BigDecimal balance);

    // === ТОП ПОЛЬЗОВАТЕЛИ ===

    /**
     * Топ пользователи по балансу
     */
    @Query("SELECT b FROM UserBalanceEntity b WHERE b.isActive = true ORDER BY b.currentBalance DESC")
    List<UserBalanceEntity> findTopUsersByBalance(Pageable pageable);

    /**
     * Топ пользователи по общим пополнениям
     */
    @Query("SELECT b FROM UserBalanceEntity b WHERE b.isActive = true ORDER BY b.totalDeposited DESC")
    List<UserBalanceEntity> findTopUsersByDeposits(Pageable pageable);

    /**
     * Топ пользователи по тратам
     */
    @Query("SELECT b FROM UserBalanceEntity b WHERE b.isActive = true ORDER BY b.totalSpent DESC")
    List<UserBalanceEntity> findTopUsersBySpending(Pageable pageable);

    // === СТАТИСТИКА И АНАЛИТИКА ===

    /**
     * Общая статистика балансов
     */
    @Query("SELECT SUM(b.currentBalance), AVG(b.currentBalance), COUNT(b) FROM UserBalanceEntity b WHERE b.isActive = true")
    List<Object[]> getBalanceStatistics();

    /**
     * Статистика по пополнениям и тратам
     */
    @Query("SELECT SUM(b.totalDeposited), SUM(b.totalSpent), AVG(b.totalDeposited), AVG(b.totalSpent) FROM UserBalanceEntity b WHERE b.isActive = true")
    List<Object[]> getDepositSpendingStatistics();

    /**
     * Активность пользователей по периодам
     */
    @Query("SELECT DATE(b.lastUpdated), COUNT(DISTINCT b.userId), SUM(b.currentBalance) FROM UserBalanceEntity b WHERE b.isActive = true AND b.lastUpdated >= :fromDate GROUP BY DATE(b.lastUpdated) ORDER BY DATE(b.lastUpdated)")
    List<Object[]> getDailyBalanceActivity(@Param("fromDate") LocalDateTime fromDate);

    /**
     * Недавно обновленные балансы
     */
    @Query("SELECT b FROM UserBalanceEntity b WHERE b.isActive = true AND b.lastUpdated >= :since ORDER BY b.lastUpdated DESC")
    List<UserBalanceEntity> findRecentlyUpdatedBalances(@Param("since") LocalDateTime since);

    // === АДМИНИСТРАТИВНЫЕ ОПЕРАЦИИ ===

    /**
     * Поиск балансов с заметками (для админа)
     */
    @Query("SELECT b FROM UserBalanceEntity b WHERE b.notes IS NOT NULL AND b.notes != '' ORDER BY b.lastUpdated DESC")
    List<UserBalanceEntity> findBalancesWithNotes();

    /**
     * Деактивация баланса
     */
    @Modifying
    @Query("UPDATE UserBalanceEntity b SET b.isActive = false WHERE b.userId = :userId")
    int deactivateBalanceByUserId(@Param("userId") Long userId);

    /**
     * Активация баланса
     */
    @Modifying
    @Query("UPDATE UserBalanceEntity b SET b.isActive = true WHERE b.userId = :userId")
    int activateBalanceByUserId(@Param("userId") Long userId);

    /**
     * Обновление заметок админа
     */
    @Modifying
    @Query("UPDATE UserBalanceEntity b SET b.notes = :notes WHERE b.userId = :userId")
    int updateBalanceNotes(@Param("userId") Long userId, @Param("notes") String notes);

    // === ПОИСК ПРОБЛЕМНЫХ БАЛАНСОВ ===

    /**
     * Поиск балансов с несоответствиями (потенциальные проблемы)
     */
    @Query("SELECT b FROM UserBalanceEntity b WHERE b.isActive = true AND (b.currentBalance < 0 OR b.totalDeposited < 0 OR b.totalSpent < 0)")
    List<UserBalanceEntity> findProblematicBalances();

    /**
     * Устаревшие балансы (давно не обновлялись)
     */
    @Query("SELECT b FROM UserBalanceEntity b WHERE b.isActive = true AND b.lastUpdated < :cutoff ORDER BY b.lastUpdated ASC")
    List<UserBalanceEntity> findStaleBalances(@Param("cutoff") LocalDateTime cutoff);

    /**
     * Балансы с высокой активностью (много операций)
     */
    @Query("SELECT b FROM UserBalanceEntity b WHERE b.isActive = true AND (b.totalDeposited + b.totalSpent) > :threshold ORDER BY (b.totalDeposited + b.totalSpent) DESC")
    List<UserBalanceEntity> findHighActivityBalances(@Param("threshold") BigDecimal threshold);

    // === МАССОВЫЕ ОПЕРАЦИИ ===

    /**
     * Деактивация устаревших балансов
     */
    @Modifying
    @Query("UPDATE UserBalanceEntity b SET b.isActive = false WHERE b.isActive = true AND b.lastUpdated < :cutoff")
    int deactivateStaleBalances(@Param("cutoff") LocalDateTime cutoff);

    /**
     * Очистка заметок у активных балансов
     */
    @Modifying
    @Query("UPDATE UserBalanceEntity b SET b.notes = null WHERE b.isActive = true AND b.notes IS NOT NULL")
    int clearNotesFromActiveBalances();

    // === ОТЧЕТЫ И ЭКСПОРТ ===

    /**
     * Балансы для отчета (сортировка по ID пользователя)
     */
    @Query("SELECT b FROM UserBalanceEntity b WHERE b.isActive = true ORDER BY b.userId ASC")
    List<UserBalanceEntity> findAllActiveBalancesForReport();

    /**
     * VIP пользователи (высокий баланс или активность)
     */
    @Query("SELECT b FROM UserBalanceEntity b WHERE b.isActive = true AND (b.currentBalance >= :minBalance OR b.totalDeposited >= :minDeposits) ORDER BY b.currentBalance DESC")
    List<UserBalanceEntity> findVipUsers(@Param("minBalance") BigDecimal minBalance,
            @Param("minDeposits") BigDecimal minDeposits);

    /**
     * Сводка по балансам для дашборда
     */
    @Query("SELECT " +
            "COUNT(b), " +
            "SUM(b.currentBalance), " +
            "AVG(b.currentBalance), " +
            "MAX(b.currentBalance), " +
            "COUNT(CASE WHEN b.currentBalance = 0 THEN 1 END) " +
            "FROM UserBalanceEntity b WHERE b.isActive = true")
    List<Object[]> getBalanceDashboardSummary();
}