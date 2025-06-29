package shit.back.service.admin.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import shit.back.service.AdminDashboardService;
import shit.back.service.AdminDashboardCacheService;
import shit.back.service.UserActivityLogService;
import shit.back.service.activity.UserActivityStatisticsService;
import shit.back.service.OrderService;
import shit.back.service.StarPackageService;
import shit.back.dto.monitoring.PerformanceMetrics;
import shit.back.dto.monitoring.SystemHealth;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;

import shit.back.dto.order.OrderStatistics;
import shit.back.service.AdminAnalyticsService;
import shit.back.service.AdminMaintenanceService;

/**
 * Унифицированный сервис для общих операций админской панели
 * Объединяет функциональность различных админских сервисов
 * Следует принципам SOLID - Dependency Inversion Principle
 */
@Service
public class AdminUnifiedService {

    private static final Logger log = LoggerFactory.getLogger(AdminUnifiedService.class);

    @Autowired
    private AdminDashboardService adminDashboardService;

    @Autowired
    private AdminDashboardCacheService adminDashboardCacheService;

    @Autowired
    private UserActivityLogService userActivityLogService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private StarPackageService starPackageService;

    /**
     * Получение сводной информации для дашборда
     */
    public Map<String, Object> getDashboardSummary() {
        Map<String, Object> summary = new HashMap<>();

        try {
            log.debug("Getting dashboard summary");

            // Получение основной информации
            AdminDashboardService.DashboardOverview overview = adminDashboardService.getDashboardOverview();
            if (overview != null) {
                summary.put("overview", overview);
                // Извлечение пользовательских счетчиков
                extractUserCounts(summary, overview);
            } else {
                setDefaultUserCounts(summary);
            }

            // Получение статистики заказов
            try {
                OrderStatistics orderStats = orderService.getOrderStatisticsOptimized();
                summary.put("orderStats", orderStats);

                if (orderStats != null) {
                    extractOrderCounts(summary, orderStats);
                } else {
                    setDefaultOrderCounts(summary);
                }
            } catch (Exception e) {
                log.warn("Failed to get order statistics", e);
                setDefaultOrderCounts(summary);
            }

            // Получение статистики пакетов
            try {
                StarPackageService.PackageStatistics packageStats = starPackageService.getPackageStatistics();
                summary.put("packageStats", packageStats);
            } catch (Exception e) {
                log.warn("Failed to get package statistics", e);
            }

            // Информация о состоянии
            summary.put("dataLoaded", true);
            summary.put("lastUpdated", LocalDateTime.now());
            summary.put("success", true);

        } catch (Exception e) {
            log.error("Error getting dashboard summary", e);
            summary.put("success", false);
            summary.put("error", e.getMessage());
            setDefaultCounts(summary);
        }

        return summary;
    }

    /**
     * Получение расширенной статистики системы
     */
    public Map<String, Object> getSystemStatistics() {
        Map<String, Object> statistics = new HashMap<>();

        try {
            log.debug("Getting system statistics");

            // Активность пользователей
            UserActivityStatisticsService.ActivityStatistics activityStats = userActivityLogService
                    .getActivityStatistics(24);
            statistics.put("activityStats", activityStats);

            // Статус платежей
            UserActivityStatisticsService.PaymentStatusDashboard paymentDashboard = userActivityLogService
                    .getPaymentStatusDashboard();
            statistics.put("paymentDashboard", paymentDashboard);

            // Производительность
            // Используйте AdminAnalyticsService.PerformanceMetrics вместо устаревшего
            // AdminDashboardService.PerformanceMetrics
            PerformanceMetrics performance = adminDashboardCacheService
                    .getPerformanceMetricsCached();
            statistics.put("performance", performance);

            // Используйте AdminMaintenanceService.SystemHealth вместо устаревшего
            // AdminDashboardService.SystemHealth
            SystemHealth systemHealth = adminDashboardService.getSystemHealth();
            statistics.put("systemHealth", systemHealth);

            statistics.put("timestamp", LocalDateTime.now());
            statistics.put("success", true);

        } catch (Exception e) {
            log.error("Error getting system statistics", e);
            statistics.put("success", false);
            statistics.put("error", e.getMessage());
        }

        return statistics;
    }

    /**
     * Обновление всех кэшей системы
     */
    public Map<String, Object> refreshAllCaches() {
        Map<String, Object> result = new HashMap<>();

        try {
            log.info("Refreshing all admin caches");

            // Очистка основного кэша
            adminDashboardCacheService.clearAllCache();

            // Прогрев кэша (асинхронно)
            adminDashboardCacheService.warmupCache();

            result.put("success", true);
            result.put("message", "All caches refreshed successfully");
            result.put("timestamp", LocalDateTime.now());

        } catch (Exception e) {
            log.error("Error refreshing all caches", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("timestamp", LocalDateTime.now());
        }

        return result;
    }

    /**
     * Получение недавних активностей с ограничением
     */
    public Map<String, Object> getRecentActivitiesLimited(int limit) {
        Map<String, Object> result = new HashMap<>();

        try {
            log.debug("Getting recent activities, limit: {}", limit);

            java.util.List<shit.back.entity.UserActivityLogEntity> activities = userActivityLogService
                    .getRecentActivities(limit);

            result.put("activities", activities);
            result.put("count", activities.size());
            result.put("limit", limit);
            result.put("timestamp", LocalDateTime.now());
            result.put("success", true);

        } catch (Exception e) {
            log.error("Error getting recent activities", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("activities", java.util.Collections.emptyList());
        }

        return result;
    }

    /**
     * Проверка состояния всех основных компонентов
     */
    public Map<String, Object> performHealthCheck() {
        Map<String, Object> healthCheck = new HashMap<>();

        try {
            log.debug("Performing admin health check");

            // Проверка доступности сервисов
            boolean dashboardServiceHealthy = checkDashboardService();
            boolean orderServiceHealthy = checkOrderService();
            boolean activityServiceHealthy = checkActivityService();
            boolean cacheServiceHealthy = checkCacheService();

            healthCheck.put("dashboardService", dashboardServiceHealthy);
            healthCheck.put("orderService", orderServiceHealthy);
            healthCheck.put("activityService", activityServiceHealthy);
            healthCheck.put("cacheService", cacheServiceHealthy);

            // Общий статус
            boolean overallHealthy = dashboardServiceHealthy && orderServiceHealthy &&
                    activityServiceHealthy && cacheServiceHealthy;

            healthCheck.put("overallHealthy", overallHealthy);
            healthCheck.put("status", overallHealthy ? "HEALTHY" : "DEGRADED");
            healthCheck.put("timestamp", LocalDateTime.now());

        } catch (Exception e) {
            log.error("Error performing health check", e);
            healthCheck.put("overallHealthy", false);
            healthCheck.put("status", "ERROR");
            healthCheck.put("error", e.getMessage());
            healthCheck.put("timestamp", LocalDateTime.now());
        }

        return healthCheck;
    }

    // Вспомогательные методы

    private void extractUserCounts(Map<String, Object> summary, AdminDashboardService.DashboardOverview overview) {
        try {
            java.lang.reflect.Field totalUsersField = overview.getClass().getDeclaredField("totalUsersCount");
            totalUsersField.setAccessible(true);
            summary.put("totalUsersCount", totalUsersField.get(overview));

            java.lang.reflect.Field activeUsersField = overview.getClass().getDeclaredField("activeUsersCount");
            activeUsersField.setAccessible(true);
            summary.put("activeUsersCount", activeUsersField.get(overview));

            java.lang.reflect.Field onlineUsersField = overview.getClass().getDeclaredField("onlineUsersCount");
            onlineUsersField.setAccessible(true);
            summary.put("onlineUsersCount", onlineUsersField.get(overview));

        } catch (Exception e) {
            log.warn("Failed to extract user counts via reflection", e);
            setDefaultUserCounts(summary);
        }
    }

    private void extractOrderCounts(Map<String, Object> summary, OrderStatistics orderStats) {
        try {
            java.lang.reflect.Field totalOrdersField = orderStats.getClass().getDeclaredField("totalOrders");
            totalOrdersField.setAccessible(true);
            summary.put("totalOrders", totalOrdersField.get(orderStats));

            java.lang.reflect.Field completedOrdersField = orderStats.getClass().getDeclaredField("completedOrders");
            completedOrdersField.setAccessible(true);
            summary.put("completedOrders", completedOrdersField.get(orderStats));

        } catch (Exception e) {
            log.warn("Failed to extract order counts via reflection", e);
            setDefaultOrderCounts(summary);
        }
    }

    private void setDefaultUserCounts(Map<String, Object> summary) {
        summary.put("totalUsersCount", 0L);
        summary.put("activeUsersCount", 0L);
        summary.put("onlineUsersCount", 0L);
    }

    private void setDefaultOrderCounts(Map<String, Object> summary) {
        summary.put("totalOrders", 0L);
        summary.put("completedOrders", 0L);
    }

    private void setDefaultCounts(Map<String, Object> summary) {
        setDefaultUserCounts(summary);
        setDefaultOrderCounts(summary);
        summary.put("dataLoaded", false);
    }

    private boolean checkDashboardService() {
        try {
            AdminDashboardService.DashboardOverview overview = adminDashboardService.getDashboardOverview();
            return overview != null;
        } catch (Exception e) {
            log.debug("Dashboard service health check failed", e);
            return false;
        }
    }

    private boolean checkOrderService() {
        try {
            OrderStatistics stats = orderService.getOrderStatistics();
            return stats != null;
        } catch (Exception e) {
            log.debug("Order service health check failed", e);
            return false;
        }
    }

    private boolean checkActivityService() {
        try {
            UserActivityStatisticsService.ActivityStatistics stats = userActivityLogService.getActivityStatistics(1);
            return stats != null;
        } catch (Exception e) {
            log.debug("Activity service health check failed", e);
            return false;
        }
    }

    private boolean checkCacheService() {
        try {
            PerformanceMetrics metrics = adminDashboardCacheService.getPerformanceMetricsCached();
            return metrics != null;
        } catch (Exception e) {
            log.debug("Cache service health check failed", e);
            return false;
        }
    }
}