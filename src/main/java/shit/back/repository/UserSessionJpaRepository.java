package shit.back.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import shit.back.entity.UserSessionEntity;
import shit.back.entity.UserSessionEntity.SessionState;
import shit.back.model.UserCountsBatchResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionJpaRepository extends JpaRepository<UserSessionEntity, Long> {

    // Поиск по userId
    Optional<UserSessionEntity> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    // Активные сессии
    List<UserSessionEntity> findByIsActiveTrueOrderByLastActivityDesc();

    Page<UserSessionEntity> findByIsActiveTrueOrderByLastActivityDesc(Pageable pageable);

    // Эффективный подсчет активных пользователей
    Long countByIsActiveTrue();

    // Поиск по состоянию
    List<UserSessionEntity> findByStateOrderByLastActivityDesc(SessionState state);

    List<UserSessionEntity> findByStateAndIsActiveTrueOrderByLastActivityDesc(SessionState state);

    // Сессии с активностью
    @Query("SELECT s FROM UserSessionEntity s WHERE s.lastActivity >= :since ORDER BY s.lastActivity DESC")
    List<UserSessionEntity> findSessionsActiveSince(@Param("since") LocalDateTime since);

    // Онлайн пользователи (активность в последние 5 минут)
    @Query("SELECT s FROM UserSessionEntity s WHERE s.isActive = true AND s.lastActivity >= :cutoff ORDER BY s.lastActivity DESC")
    List<UserSessionEntity> findOnlineUsers(@Param("cutoff") LocalDateTime cutoff);

    Long countByIsActiveTrueAndLastActivityAfter(LocalDateTime cutoff);

    // Истекшие сессии
    @Query("SELECT s FROM UserSessionEntity s WHERE s.isActive = true AND s.lastActivity < :cutoff")
    List<UserSessionEntity> findExpiredSessions(@Param("cutoff") LocalDateTime cutoff);

    // Деактивация истекших сессий
    @Modifying
    @Query("UPDATE UserSessionEntity s SET s.isActive = false, s.state = 'EXPIRED' WHERE s.isActive = true AND s.lastActivity < :cutoff")
    int deactivateExpiredSessions(@Param("cutoff") LocalDateTime cutoff);

    // Статистика по состояниям
    @Query("SELECT s.state, COUNT(s) FROM UserSessionEntity s WHERE s.isActive = true GROUP BY s.state")
    List<Object[]> getActiveSessionStatistics();

    // Топ активные пользователи
    @Query("SELECT s FROM UserSessionEntity s WHERE s.isActive = true ORDER BY s.totalOrders DESC, s.totalStarsPurchased DESC")
    List<UserSessionEntity> findTopActiveUsers(Pageable pageable);

    // Поиск по имени пользователя
    @Query("SELECT s FROM UserSessionEntity s WHERE " +
            "LOWER(s.username) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(s.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(s.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<UserSessionEntity> searchUsers(@Param("searchTerm") String searchTerm, Pageable pageable);

    // Статистика по периодам активности
    @Query("SELECT DATE(s.lastActivity), COUNT(DISTINCT s.userId) FROM UserSessionEntity s WHERE s.lastActivity >= :fromDate GROUP BY DATE(s.lastActivity) ORDER BY DATE(s.lastActivity)")
    List<Object[]> getDailyActiveUsers(@Param("fromDate") LocalDateTime fromDate);

    // Новые пользователи
    @Query("SELECT s FROM UserSessionEntity s WHERE s.createdAt >= :since ORDER BY s.createdAt DESC")
    List<UserSessionEntity> findNewUsers(@Param("since") LocalDateTime since);

    Long countByCreatedAtAfter(LocalDateTime since);

    // Пользователи с заказами
    List<UserSessionEntity> findByTotalOrdersGreaterThanOrderByTotalOrdersDesc(Integer minOrders);

    // Средняя активность
    @Query("SELECT AVG(s.totalOrders), AVG(s.totalStarsPurchased) FROM UserSessionEntity s WHERE s.isActive = true")
    List<Object[]> getAverageUserActivity();

    // Языки пользователей
    @Query("SELECT s.languageCode, COUNT(s) FROM UserSessionEntity s WHERE s.languageCode IS NOT NULL GROUP BY s.languageCode ORDER BY COUNT(s) DESC")
    List<Object[]> getLanguageDistribution();

    // Пользователи в определенном состоянии дольше времени
    @Query("SELECT s FROM UserSessionEntity s WHERE s.state = :state AND s.updatedAt < :cutoff")
    List<UserSessionEntity> findUsersStuckInState(@Param("state") SessionState state,
            @Param("cutoff") LocalDateTime cutoff);

    // Пользователи с текущими заказами
    List<UserSessionEntity> findByCurrentOrderIdIsNotNull();

    @Query("SELECT COUNT(s) FROM UserSessionEntity s WHERE s.currentOrderId IS NOT NULL")
    Long countUsersWithPendingOrders();

    // VIP пользователи (много заказов или звезд)
    @Query("SELECT s FROM UserSessionEntity s WHERE s.totalOrders >= :minOrders OR s.totalStarsPurchased >= :minStars ORDER BY s.totalStarsPurchased DESC")
    List<UserSessionEntity> findVipUsers(@Param("minOrders") Integer minOrders, @Param("minStars") Long minStars);

    // Обновление активности пользователя
    @Modifying
    @Query("UPDATE UserSessionEntity s SET s.lastActivity = :now, s.isActive = true WHERE s.userId = :userId")
    int updateUserActivity(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    // Сессии по IP адресам (для аналитики)
    @Query("SELECT s.ipAddress, COUNT(DISTINCT s.userId) FROM UserSessionEntity s WHERE s.ipAddress IS NOT NULL GROUP BY s.ipAddress HAVING COUNT(DISTINCT s.userId) > 1")
    List<Object[]> findSharedIpAddresses();

    // Время жизни сессий
    @Query("SELECT AVG((EXTRACT(EPOCH FROM s.updatedAt) - EXTRACT(EPOCH FROM s.createdAt))/3600) FROM UserSessionEntity s WHERE s.state = 'COMPLETED'")
    Double getAverageSessionDurationHours();

    // Батч-запрос для получения всех счетчиков пользователей одним SQL запросом
    // Решает N+1 Query проблему в AdminDashboardService
    @Query("SELECT " +
            "COUNT(*) as totalUsers, " +
            "COUNT(CASE WHEN s.isActive = true AND s.lastActivity > :activeThreshold THEN 1 END) as activeUsers, " +
            "COUNT(CASE WHEN s.isActive = true AND s.lastActivity > :onlineThreshold THEN 1 END) as onlineUsers " +
            "FROM UserSessionEntity s")
    UserCountsBatchResult getUserCountsBatch(@Param("activeThreshold") LocalDateTime activeThreshold,
            @Param("onlineThreshold") LocalDateTime onlineThreshold);
}
