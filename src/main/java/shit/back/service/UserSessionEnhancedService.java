package shit.back.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shit.back.entity.UserSessionEntity;
import shit.back.model.UserSession;
import shit.back.repository.UserSessionJpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Enhanced service for managing user sessions with PostgreSQL persistence
 */
@Slf4j
@Service
@Transactional
public class UserSessionEnhancedService {
    
    @Autowired
    private UserSessionJpaRepository sessionRepository;
    
    /**
     * Create or update user session
     */
    public UserSessionEntity createOrUpdateSession(UserSession userSession) {
        log.info("Creating/updating session for user {}", userSession.getUserId());
        
        Optional<UserSessionEntity> existingOpt = sessionRepository.findByUserId(userSession.getUserId());
        
        UserSessionEntity entity;
        if (existingOpt.isPresent()) {
            entity = existingOpt.get();
            entity.setUsername(userSession.getUsername());
            entity.setFirstName(userSession.getFirstName());
            entity.setLastName(userSession.getLastName());
            
            // Convert UserSession.SessionState to UserSessionEntity.SessionState
            if (userSession.getState() != null) {
                entity.setState(convertSessionState(userSession.getState()));
            }
            
            if (userSession.getOrderId() != null) {
                entity.setCurrentOrderId(userSession.getOrderId());
            }
            
            entity.updateActivity();
        } else {
            entity = new UserSessionEntity(
                    userSession.getUserId(),
                    userSession.getUsername(),
                    userSession.getFirstName(),
                    userSession.getLastName()
            );
            
            if (userSession.getState() != null) {
                entity.setState(convertSessionState(userSession.getState()));
            }
            
            if (userSession.getOrderId() != null) {
                entity.setCurrentOrderId(userSession.getOrderId());
            }
        }
        
        UserSessionEntity saved = sessionRepository.save(entity);
        log.info("Session for user {} saved with ID: {}", userSession.getUserId(), saved.getId());
        return saved;
    }
    
    /**
     * Convert UserSession.SessionState to UserSessionEntity.SessionState
     */
    private UserSessionEntity.SessionState convertSessionState(UserSession.SessionState state) {
        return switch (state) {
            case IDLE -> UserSessionEntity.SessionState.IDLE;
            case SELECTING_PACKAGE -> UserSessionEntity.SessionState.SELECTING_PACKAGE;
            case CONFIRMING_ORDER -> UserSessionEntity.SessionState.CONFIRMING_ORDER;
            case AWAITING_PAYMENT -> UserSessionEntity.SessionState.AWAITING_PAYMENT;
            case PAYMENT_PROCESSING -> UserSessionEntity.SessionState.PAYMENT_PROCESSING;
            case COMPLETED -> UserSessionEntity.SessionState.COMPLETED;
        };
    }
    
    /**
     * Get session by user ID
     */
    @Transactional(readOnly = true)
    public Optional<UserSessionEntity> getSessionByUserId(Long userId) {
        return sessionRepository.findByUserId(userId);
    }
    
    /**
     * Get all active sessions
     */
    @Transactional(readOnly = true)
    public List<UserSessionEntity> getActiveSessions() {
        return sessionRepository.findByIsActiveTrueOrderByLastActivityDesc();
    }
    
    /**
     * Get recent active sessions
     */
    @Transactional(readOnly = true)
    public List<UserSessionEntity> getRecentActiveSessions(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return sessionRepository.findSessionsActiveSince(since);
    }
    
    /**
     * Get online users (active in last 5 minutes)
     */
    @Transactional(readOnly = true)
    public List<UserSessionEntity> getOnlineUsers() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);
        return sessionRepository.findOnlineUsers(cutoff);
    }
    
    /**
     * Get sessions by current state
     */
    @Transactional(readOnly = true)
    public List<UserSessionEntity> getSessionsByState(UserSessionEntity.SessionState state) {
        return sessionRepository.findByStateOrderByLastActivityDesc(state);
    }
    
    /**
     * Get paginated sessions
     */
    @Transactional(readOnly = true)
    public Page<UserSessionEntity> getSessions(Pageable pageable) {
        return sessionRepository.findAll(pageable);
    }
    
    /**
     * Search sessions
     */
    @Transactional(readOnly = true)
    public Page<UserSessionEntity> searchSessions(String searchTerm, Pageable pageable) {
        return sessionRepository.searchUsers(searchTerm, pageable);
    }
    
    /**
     * Get top active users
     */
    @Transactional(readOnly = true)
    public List<UserSessionEntity> getTopActiveUsers(int limit) {
        return sessionRepository.findTopActiveUsers(PageRequest.of(0, limit));
    }
    
    /**
     * Update user activity
     */
    @Transactional
    public void updateUserActivity(Long userId) {
        int updated = sessionRepository.updateUserActivity(userId, LocalDateTime.now());
        if (updated > 0) {
            log.debug("Updated activity for user {}", userId);
        }
    }
    
    /**
     * Deactivate expired sessions
     */
    @Transactional
    public int deactivateExpiredSessions(int hours) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(hours);
        int deactivated = sessionRepository.deactivateExpiredSessions(cutoff);
        if (deactivated > 0) {
            log.info("Deactivated {} expired sessions", deactivated);
        }
        return deactivated;
    }
    
    /**
     * Get new users
     */
    @Transactional(readOnly = true)
    public List<UserSessionEntity> getNewUsers(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return sessionRepository.findNewUsers(since);
    }
    
    /**
     * Get users with pending orders
     */
    @Transactional(readOnly = true)
    public List<UserSessionEntity> getUsersWithPendingOrders() {
        return sessionRepository.findByCurrentOrderIdIsNotNull();
    }
    
    /**
     * Get VIP users
     */
    @Transactional(readOnly = true)
    public List<UserSessionEntity> getVipUsers(Integer minOrders, Long minStars) {
        return sessionRepository.findVipUsers(minOrders, minStars);
    }
    
    /**
     * Get users stuck in state
     */
    @Transactional(readOnly = true)
    public List<UserSessionEntity> getUsersStuckInState(UserSessionEntity.SessionState state, int hours) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(hours);
        return sessionRepository.findUsersStuckInState(state, cutoff);
    }
    
    // Statistics methods
    
    /**
     * Get total users count
     */
    @Transactional(readOnly = true)
    public long getTotalUsersCount() {
        return sessionRepository.count();
    }
    
    /**
     * Get active users count (optimized)
     */
    @Transactional(readOnly = true)
    public long getActiveUsersCount() {
        return sessionRepository.countByIsActiveTrue();
    }
    
    /**
     * Get online users count
     */
    @Transactional(readOnly = true)
    public long getOnlineUsersCount() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);
        return sessionRepository.countByIsActiveTrueAndLastActivityAfter(cutoff);
    }
    
    /**
     * Get new users count for period
     */
    @Transactional(readOnly = true)
    public long getNewUsersCount(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return sessionRepository.countByCreatedAtAfter(since);
    }
    
    /**
     * Get users with pending orders count
     */
    @Transactional(readOnly = true)
    public long getUsersWithPendingOrdersCount() {
        return sessionRepository.countUsersWithPendingOrders();
    }
    
    /**
     * Get users by language statistics
     */
    @Transactional(readOnly = true)
    public List<LanguageStats> getUsersByLanguage() {
        List<Object[]> results = sessionRepository.getLanguageDistribution();
        
        return results.stream()
                .map(row -> LanguageStats.builder()
                        .languageCode((String) row[0])
                        .userCount(((Number) row[1]).longValue())
                        .build())
                .toList();
    }
    
    /**
     * Get daily active users statistics
     */
    @Transactional(readOnly = true)
    public List<DailyActiveUsers> getDailyActiveUsers(int days) {
        LocalDateTime fromDate = LocalDateTime.now().minusDays(days);
        List<Object[]> results = sessionRepository.getDailyActiveUsers(fromDate);
        
        return results.stream()
                .map(row -> DailyActiveUsers.builder()
                        .date((java.sql.Date) row[0])
                        .activeUsers(((Number) row[1]).longValue())
                        .build())
                .toList();
    }
    
    /**
     * Get active session statistics by state
     */
    @Transactional(readOnly = true)
    public List<SessionStateStats> getActiveSessionStatistics() {
        List<Object[]> results = sessionRepository.getActiveSessionStatistics();
        
        return results.stream()
                .map(row -> SessionStateStats.builder()
                        .state((UserSessionEntity.SessionState) row[0])
                        .count(((Number) row[1]).longValue())
                        .build())
                .toList();
    }
    
    /**
     * Get average user activity
     */
    @Transactional(readOnly = true)
    public UserActivityAverages getAverageUserActivity() {
        List<Object[]> results = sessionRepository.getAverageUserActivity();
        if (!results.isEmpty()) {
            Object[] row = results.get(0);
            return UserActivityAverages.builder()
                    .averageOrders(row[0] != null ? ((Number) row[0]).doubleValue() : 0.0)
                    .averageStarsPurchased(row[1] != null ? ((Number) row[1]).doubleValue() : 0.0)
                    .build();
        }
        return UserActivityAverages.builder()
                .averageOrders(0.0)
                .averageStarsPurchased(0.0)
                .build();
    }
    
    /**
     * Get average session duration
     */
    @Transactional(readOnly = true)
    public Double getAverageSessionDurationHours() {
        Double duration = sessionRepository.getAverageSessionDurationHours();
        return duration != null ? duration : 0.0;
    }
    
    /**
     * Get user session statistics
     */
    @Transactional(readOnly = true)
    public UserSessionStatistics getUserSessionStatistics() {
        UserActivityAverages averages = getAverageUserActivity();
        
        return UserSessionStatistics.builder()
                .totalUsers(getTotalUsersCount())
                .activeUsers(getActiveUsersCount())
                .onlineUsers(getOnlineUsersCount())
                .inactiveUsers(getTotalUsersCount() - getActiveUsersCount())
                .newUsersToday(getNewUsersCount(1))
                .newUsersThisWeek(getNewUsersCount(7))
                .newUsersThisMonth(getNewUsersCount(30))
                .usersWithPendingOrders(getUsersWithPendingOrdersCount())
                .averageOrdersPerUser(averages.getAverageOrders())
                .averageStarsPerUser(averages.getAverageStarsPurchased())
                .averageSessionDurationHours(getAverageSessionDurationHours())
                .build();
    }
    
    /**
     * Scheduled task for automatic cleanup of expired sessions
     * Runs every hour to maintain database cleanliness
     */
    @Scheduled(fixedRate = 3600000) // Every hour (3600000 ms)
    @Transactional
    public void scheduledCleanupExpiredSessions() {
        try {
            int deactivated = deactivateExpiredSessions(48); // Deactivate sessions older than 48 hours
            if (deactivated > 0) {
                log.info("Scheduled cleanup: deactivated {} expired sessions", deactivated);
            }
        } catch (Exception e) {
            log.error("Error during scheduled session cleanup", e);
        }
    }
    
    // Data transfer objects
    
    @lombok.Data
    @lombok.Builder
    public static class UserSessionStatistics {
        private long totalUsers;
        private long activeUsers;
        private long onlineUsers;
        private long inactiveUsers;
        private long newUsersToday;
        private long newUsersThisWeek;
        private long newUsersThisMonth;
        private long usersWithPendingOrders;
        private Double averageOrdersPerUser;
        private Double averageStarsPerUser;
        private Double averageSessionDurationHours;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class LanguageStats {
        private String languageCode;
        private Long userCount;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class DailyActiveUsers {
        private java.sql.Date date;
        private Long activeUsers;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class SessionStateStats {
        private UserSessionEntity.SessionState state;
        private Long count;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class UserActivityAverages {
        private Double averageOrders;
        private Double averageStarsPurchased;
    }
}
