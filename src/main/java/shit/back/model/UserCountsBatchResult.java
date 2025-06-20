package shit.back.model;

/**
 * Record для результата батч-запроса счетчиков пользователей
 * Используется для решения N+1 Query проблемы в AdminDashboardService
 */
public record UserCountsBatchResult(
        long totalUsers,
        long activeUsers,
        long onlineUsers) {
}