package shit.back.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import shit.back.entity.UserActivityLogEntity;
import shit.back.entity.UserActivityLogEntity.ActionType;
import shit.back.entity.UserActivityLogEntity.LogCategory;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserActivityLogJpaRepository extends JpaRepository<UserActivityLogEntity, Long> {

       // Поиск по userId
       List<UserActivityLogEntity> findByUserIdOrderByTimestampDesc(Long userId);

       Page<UserActivityLogEntity> findByUserIdOrderByTimestampDesc(Long userId, Pageable pageable);

       // Только ключевые действия (по умолчанию)
       @Query("SELECT a FROM UserActivityLogEntity a WHERE a.isKeyAction = true ORDER BY a.timestamp DESC")
       List<UserActivityLogEntity> findKeyActionsOrderByTimestampDesc();

       @Query("SELECT a FROM UserActivityLogEntity a WHERE a.isKeyAction = true ORDER BY a.timestamp DESC")
       Page<UserActivityLogEntity> findKeyActionsOrderByTimestampDesc(Pageable pageable);

       // Все действия
       List<UserActivityLogEntity> findAllByOrderByTimestampDesc();

       Page<UserActivityLogEntity> findAllByOrderByTimestampDesc(Pageable pageable);

       // Поиск по временному диапазону
       @Query("SELECT a FROM UserActivityLogEntity a WHERE a.timestamp >= :fromTime ORDER BY a.timestamp DESC")
       List<UserActivityLogEntity> findActivitiesSince(@Param("fromTime") LocalDateTime fromTime);

       @Query("SELECT a FROM UserActivityLogEntity a WHERE a.timestamp >= :fromTime AND a.isKeyAction = true ORDER BY a.timestamp DESC")
       List<UserActivityLogEntity> findKeyActivitiesSince(@Param("fromTime") LocalDateTime fromTime);

       @Query("SELECT a FROM UserActivityLogEntity a WHERE a.timestamp BETWEEN :fromTime AND :toTime ORDER BY a.timestamp DESC")
       List<UserActivityLogEntity> findActivitiesBetween(@Param("fromTime") LocalDateTime fromTime,
                     @Param("toTime") LocalDateTime toTime);

       // Поиск по типу действия
       List<UserActivityLogEntity> findByActionTypeOrderByTimestampDesc(ActionType actionType);

       List<UserActivityLogEntity> findByActionTypeInOrderByTimestampDesc(List<ActionType> actionTypes);

       // Поиск с фильтрами
       @Query("SELECT a FROM UserActivityLogEntity a WHERE " +
                     "(:showAll = true OR a.isKeyAction = true) AND " +
                     "(:fromTime IS NULL OR a.timestamp >= :fromTime) AND " +
                     "(:toTime IS NULL OR a.timestamp <= :toTime) AND " +
                     "(:actionTypes IS NULL OR a.actionType IN :actionTypes) AND " +
                     "(:searchTerm IS NULL OR " +
                     " LOWER(a.username) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
                     " LOWER(a.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
                     " LOWER(a.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
                     " LOWER(a.orderId) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
                     "ORDER BY a.timestamp DESC")
       Page<UserActivityLogEntity> findWithFilters(
                     @Param("showAll") boolean showAll,
                     @Param("fromTime") LocalDateTime fromTime,
                     @Param("toTime") LocalDateTime toTime,
                     @Param("actionTypes") List<ActionType> actionTypes,
                     @Param("searchTerm") String searchTerm,
                     Pageable pageable);

       // Последние активности для live feed
       @Query("SELECT a FROM UserActivityLogEntity a WHERE a.timestamp >= :since ORDER BY a.timestamp DESC")
       List<UserActivityLogEntity> findRecentActivities(@Param("since") LocalDateTime since);

       @Query("SELECT a FROM UserActivityLogEntity a WHERE a.isKeyAction = true AND a.timestamp >= :since ORDER BY a.timestamp DESC")
       List<UserActivityLogEntity> findRecentKeyActivities(@Param("since") LocalDateTime since);

       // Статистика по действиям
       @Query("SELECT a.actionType, COUNT(a) FROM UserActivityLogEntity a WHERE a.timestamp >= :fromTime GROUP BY a.actionType ORDER BY COUNT(a) DESC")
       List<Object[]> getActionTypeStatistics(@Param("fromTime") LocalDateTime fromTime);

       @Query("SELECT a.actionType, COUNT(a) FROM UserActivityLogEntity a WHERE a.timestamp >= :fromTime AND a.isKeyAction = true GROUP BY a.actionType ORDER BY COUNT(a) DESC")
       List<Object[]> getKeyActionTypeStatistics(@Param("fromTime") LocalDateTime fromTime);

       // Статистика по пользователям
       @Query("SELECT a.userId, a.username, COUNT(a) FROM UserActivityLogEntity a WHERE a.timestamp >= :fromTime GROUP BY a.userId, a.username ORDER BY COUNT(a) DESC")
       List<Object[]> getMostActiveUsers(@Param("fromTime") LocalDateTime fromTime);

       // Активности по заказам
       List<UserActivityLogEntity> findByOrderIdOrderByTimestampDesc(String orderId);

       @Query("SELECT a FROM UserActivityLogEntity a WHERE a.orderId IS NOT NULL AND a.timestamp >= :fromTime ORDER BY a.timestamp DESC")
       List<UserActivityLogEntity> findOrderRelatedActivities(@Param("fromTime") LocalDateTime fromTime);

       // Статистика платежей
       @Query("SELECT a.paymentMethod, COUNT(a), SUM(a.orderAmount) FROM UserActivityLogEntity a WHERE a.actionType = 'PAYMENT_COMPLETED' AND a.timestamp >= :fromTime GROUP BY a.paymentMethod")
       List<Object[]> getPaymentMethodStatistics(@Param("fromTime") LocalDateTime fromTime);

       @Query("SELECT DATE(a.timestamp), COUNT(a), SUM(a.orderAmount) FROM UserActivityLogEntity a WHERE a.actionType = 'PAYMENT_COMPLETED' AND a.timestamp >= :fromTime GROUP BY DATE(a.timestamp) ORDER BY DATE(a.timestamp)")
       List<Object[]> getDailyPaymentStatistics(@Param("fromTime") LocalDateTime fromTime);

       // Подсчет активностей
       Long countByTimestampAfter(LocalDateTime fromTime);

       Long countByIsKeyActionTrueAndTimestampAfter(LocalDateTime fromTime);

       @Query("SELECT COUNT(a) FROM UserActivityLogEntity a WHERE a.actionType IN :actionTypes AND a.timestamp >= :fromTime")
       Long countByActionTypesAndTimestampAfter(@Param("actionTypes") List<ActionType> actionTypes,
                     @Param("fromTime") LocalDateTime fromTime);

       // Очистка старых логов
       @Modifying
       @Query("DELETE FROM UserActivityLogEntity a WHERE a.timestamp < :cutoffTime")
       int deleteOldActivities(@Param("cutoffTime") LocalDateTime cutoffTime);

       @Modifying
       @Query("DELETE FROM UserActivityLogEntity a WHERE a.isKeyAction = false AND a.timestamp < :cutoffTime")
       int deleteOldNonKeyActivities(@Param("cutoffTime") LocalDateTime cutoffTime);

       // Live feed для real-time обновлений
       @Query("SELECT a FROM UserActivityLogEntity a WHERE a.id > :lastId ORDER BY a.timestamp DESC LIMIT 50")
       List<UserActivityLogEntity> findNewActivitiesSinceId(@Param("lastId") Long lastId);

       @Query("SELECT a FROM UserActivityLogEntity a WHERE a.isKeyAction = true AND a.id > :lastId ORDER BY a.timestamp DESC LIMIT 30")
       List<UserActivityLogEntity> findNewKeyActivitiesSinceId(@Param("lastId") Long lastId);

       // Поиск активностей по периодам для аналитики
       @Query("SELECT HOUR(a.timestamp), COUNT(a) FROM UserActivityLogEntity a WHERE a.timestamp >= :fromTime GROUP BY HOUR(a.timestamp) ORDER BY HOUR(a.timestamp)")
       List<Object[]> getHourlyActivityDistribution(@Param("fromTime") LocalDateTime fromTime);

       @Query("SELECT DAYOFWEEK(a.timestamp), COUNT(a) FROM UserActivityLogEntity a WHERE a.timestamp >= :fromTime GROUP BY DAYOFWEEK(a.timestamp) ORDER BY DAYOFWEEK(a.timestamp)")
       List<Object[]> getWeeklyActivityDistribution(@Param("fromTime") LocalDateTime fromTime);

       // Проблемные активности (для мониторинга)
       @Query("SELECT a FROM UserActivityLogEntity a WHERE a.actionType = 'PAYMENT_FAILED' AND a.timestamp >= :fromTime ORDER BY a.timestamp DESC")
       List<UserActivityLogEntity> findFailedPayments(@Param("fromTime") LocalDateTime fromTime);

       @Query("SELECT a FROM UserActivityLogEntity a WHERE a.actionType = 'ORDER_CANCELLED' AND a.timestamp >= :fromTime ORDER BY a.timestamp DESC")
       List<UserActivityLogEntity> findCancelledOrders(@Param("fromTime") LocalDateTime fromTime);

       // Пользователи, зависшие в состоянии
       @Query("SELECT a.userId, a.username, MAX(a.timestamp) FROM UserActivityLogEntity a WHERE a.actionType = 'PAYMENT_INITIATED' AND a.userId NOT IN "
                     +
                     "(SELECT a2.userId FROM UserActivityLogEntity a2 WHERE a2.actionType IN ('PAYMENT_COMPLETED', 'PAYMENT_FAILED', 'ORDER_CANCELLED') AND a2.timestamp > a.timestamp) "
                     +
                     "GROUP BY a.userId, a.username HAVING MAX(a.timestamp) < :cutoffTime ORDER BY MAX(a.timestamp)")
       List<Object[]> findUsersStuckInPayment(@Param("cutoffTime") LocalDateTime cutoffTime);

       // Export данных
       @Query("SELECT a FROM UserActivityLogEntity a WHERE " +
                     "a.timestamp BETWEEN :fromTime AND :toTime AND " +
                     "(:includeNonKey = true OR a.isKeyAction = true) " +
                     "ORDER BY a.timestamp")
       List<UserActivityLogEntity> findForExport(
                     @Param("fromTime") LocalDateTime fromTime,
                     @Param("toTime") LocalDateTime toTime,
                     @Param("includeNonKey") boolean includeNonKey);

       // ==================== МЕТОДЫ ДЛЯ ФИЛЬТРАЦИИ ПО КАТЕГОРИЯМ ЛОГОВ
       // ====================

       // Поиск по категории логов
       List<UserActivityLogEntity> findByLogCategoryOrderByTimestampDesc(LogCategory logCategory);

       Page<UserActivityLogEntity> findByLogCategoryOrderByTimestampDesc(LogCategory logCategory, Pageable pageable);

       // Поиск по категории и временному диапазону
       @Query("SELECT a FROM UserActivityLogEntity a WHERE a.logCategory = :logCategory AND a.timestamp BETWEEN :fromTime AND :toTime ORDER BY a.timestamp DESC")
       List<UserActivityLogEntity> findByLogCategoryAndTimestampBetween(
                     @Param("logCategory") LogCategory logCategory,
                     @Param("fromTime") LocalDateTime fromTime,
                     @Param("toTime") LocalDateTime toTime);

       @Query("SELECT a FROM UserActivityLogEntity a WHERE a.logCategory = :logCategory AND a.timestamp >= :fromTime ORDER BY a.timestamp DESC")
       List<UserActivityLogEntity> findByLogCategoryAndTimestampAfter(
                     @Param("logCategory") LogCategory logCategory,
                     @Param("fromTime") LocalDateTime fromTime);

       // Комбинированный поиск с фильтрами по категориям
       @Query("SELECT a FROM UserActivityLogEntity a WHERE " +
                     "(:showAll = true OR a.isKeyAction = true) AND " +
                     "(:fromTime IS NULL OR a.timestamp >= :fromTime) AND " +
                     "(:toTime IS NULL OR a.timestamp <= :toTime) AND " +
                     "(:actionTypes IS NULL OR a.actionType IN :actionTypes) AND " +
                     "(:logCategories IS NULL OR a.logCategory IN :logCategories) AND " +
                     "(:searchTerm IS NULL OR " +
                     " LOWER(a.username) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
                     " LOWER(a.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
                     " LOWER(a.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
                     " LOWER(a.orderId) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
                     "ORDER BY a.timestamp DESC")
       Page<UserActivityLogEntity> findWithFiltersAndCategories(
                     @Param("showAll") boolean showAll,
                     @Param("fromTime") LocalDateTime fromTime,
                     @Param("toTime") LocalDateTime toTime,
                     @Param("actionTypes") List<ActionType> actionTypes,
                     @Param("logCategories") List<LogCategory> logCategories,
                     @Param("searchTerm") String searchTerm,
                     Pageable pageable);

       // Статистика по категориям
       @Query("SELECT a.logCategory, COUNT(a) FROM UserActivityLogEntity a WHERE a.timestamp >= :fromTime GROUP BY a.logCategory ORDER BY COUNT(a) DESC")
       List<Object[]> getLogCategoryStatistics(@Param("fromTime") LocalDateTime fromTime);

       @Query("SELECT a.logCategory, a.actionType, COUNT(a) FROM UserActivityLogEntity a WHERE a.timestamp >= :fromTime GROUP BY a.logCategory, a.actionType ORDER BY a.logCategory, COUNT(a) DESC")
       List<Object[]> getCategoryActionTypeStatistics(@Param("fromTime") LocalDateTime fromTime);

       // Подсчет по категориям
       Long countByLogCategory(LogCategory logCategory);

       Long countByLogCategoryAndTimestampAfter(LogCategory logCategory, LocalDateTime fromTime);

       @Query("SELECT COUNT(a) FROM UserActivityLogEntity a WHERE a.logCategory = :logCategory AND a.isKeyAction = true AND a.timestamp >= :fromTime")
       Long countByLogCategoryAndIsKeyActionTrueAndTimestampAfter(
                     @Param("logCategory") LogCategory logCategory,
                     @Param("fromTime") LocalDateTime fromTime);

       // Telegram Bot специфичные методы
       @Query("SELECT a FROM UserActivityLogEntity a WHERE a.logCategory = 'TELEGRAM_BOT' AND a.timestamp >= :fromTime ORDER BY a.timestamp DESC")
       List<UserActivityLogEntity> findTelegramBotActivities(@Param("fromTime") LocalDateTime fromTime);

       @Query("SELECT a FROM UserActivityLogEntity a WHERE a.logCategory = 'TELEGRAM_BOT' AND a.timestamp >= :fromTime ORDER BY a.timestamp DESC")
       Page<UserActivityLogEntity> findTelegramBotActivities(@Param("fromTime") LocalDateTime fromTime,
                     Pageable pageable);

       // Application специфичные методы
       @Query("SELECT a FROM UserActivityLogEntity a WHERE a.logCategory = 'APPLICATION' AND a.timestamp >= :fromTime ORDER BY a.timestamp DESC")
       List<UserActivityLogEntity> findApplicationActivities(@Param("fromTime") LocalDateTime fromTime);

       @Query("SELECT a FROM UserActivityLogEntity a WHERE a.logCategory = 'APPLICATION' AND a.timestamp >= :fromTime ORDER BY a.timestamp DESC")
       Page<UserActivityLogEntity> findApplicationActivities(@Param("fromTime") LocalDateTime fromTime,
                     Pageable pageable);

       // Очистка по категориям
       @Modifying
       @Query("DELETE FROM UserActivityLogEntity a WHERE a.logCategory = :logCategory AND a.timestamp < :cutoffTime")
       int deleteOldActivitiesByCategory(@Param("logCategory") LogCategory logCategory,
                     @Param("cutoffTime") LocalDateTime cutoffTime);
}
