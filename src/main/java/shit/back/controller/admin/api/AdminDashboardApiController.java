package shit.back.controller.admin.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import shit.back.web.controller.admin.AdminBaseController;
import shit.back.web.controller.admin.AdminDashboardOperations;
import shit.back.dto.monitoring.PerformanceMetrics;
import shit.back.dto.order.OrderStatistics;
import shit.back.service.AdminDashboardService;
import shit.back.service.AdminDashboardCacheService;
import shit.back.service.UserActivityLogService;
import shit.back.service.activity.UserActivityStatisticsService;
import shit.back.service.StarPackageService;
import shit.back.service.OrderService;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * REST API контроллер для дашборда админ панели
 * Содержит только JSON API endpoints для дашборда
 * Следует принципам SOLID и чистой архитектуры
 */
@RestController
@RequestMapping("/admin/api/dashboard")
public class AdminDashboardApiController extends AdminBaseController implements AdminDashboardOperations {

    private static final Logger log = LoggerFactory.getLogger(AdminDashboardApiController.class);

    @Autowired
    private AdminDashboardService adminDashboardService;

    @Autowired
    private AdminDashboardCacheService adminDashboardCacheService;

    @Autowired
    private UserActivityLogService userActivityLogService;

    @Autowired
    private StarPackageService starPackageService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private Environment environment;

    /**
     * Получение базовых данных dashboard
     */
    @GetMapping(value = "/overview", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getDashboardOverviewApi(HttpServletRequest request) {
        try {
            log.debug("API: Getting dashboard overview");

            // Аутентификация
            if (!validateApiAuthentication(request)) {
                log.warn("Authentication failed for dashboard overview");
                return ResponseEntity.status(401)
                        .body(createErrorResponse("Unauthorized access", null));
            }

            AdminDashboardService.DashboardOverview overview = getDashboardOverview();
            PerformanceMetrics performance = getPerformanceMetrics();

            Map<String, Object> dashboardData = Map.of(
                    "overview", overview != null ? overview : Map.of(),
                    "performance", performance != null ? performance : Map.of(),
                    "lastUpdated", LocalDateTime.now(),
                    "success", true);

            // Логирование доступа
            logAdminActivity(request, "API_DASHBOARD_OVERVIEW",
                    "Получение обзора дашборда через API");

            return ResponseEntity.ok(dashboardData);

        } catch (Exception e) {
            log.error("API: Error getting dashboard overview", e);
            return ResponseEntity.status(500)
                    .body(createErrorResponse("Failed to get dashboard overview", e));
        }
    }

    /**
     * Получение полных данных dashboard (асинхронно)
     */
    @GetMapping(value = "/full", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<ResponseEntity<Object>> getFullDashboardDataApi(HttpServletRequest request) {
        log.debug("API: Getting full dashboard data asynchronously");

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Аутентификация
                if (!validateApiAuthentication(request)) {
                    return ResponseEntity.status(401)
                            .body(createErrorResponse("Unauthorized access", null));
                }

                AdminDashboardCacheService.FullDashboardDataCached fullData = getFullDashboardData();

                // Добавляем данные о заказах
                java.util.List<shit.back.entity.OrderEntity> recentOrders = java.util.Collections.emptyList();
                OrderStatistics orderStats = null;
                Long totalOrders = 0L;
                Long completedOrdersCount = 0L;

                try {
                    recentOrders = orderService.getRecentOrders(7);
                    orderStats = orderService.getOrderStatistics();

                    if (orderStats != null) {
                        // Используем рефлексию для доступа к полям
                        try {
                            java.lang.reflect.Field totalOrdersField = orderStats.getClass()
                                    .getDeclaredField("totalOrders");
                            totalOrdersField.setAccessible(true);
                            totalOrders = (Long) totalOrdersField.get(orderStats);

                            java.lang.reflect.Field completedOrdersField = orderStats.getClass()
                                    .getDeclaredField("completedOrders");
                            completedOrdersField.setAccessible(true);
                            completedOrdersCount = (Long) completedOrdersField.get(orderStats);
                        } catch (Exception reflectionEx) {
                            log.warn("Failed to extract order stats via reflection", reflectionEx);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to load orders data for API, using defaults", e);
                }

                if (fullData != null) {
                    Map<String, Object> result = new java.util.HashMap<>();
                    result.put("success", true);
                    result.put("data", fullData);
                    result.put("recentOrders", recentOrders);
                    result.put("orderStats", orderStats);
                    result.put("totalOrders", totalOrders);
                    result.put("completedOrdersCount", completedOrdersCount);
                    result.put("timestamp", LocalDateTime.now());

                    // Логирование доступа
                    logAdminActivity(request, "API_DASHBOARD_FULL",
                            "Получение полных данных дашборда через API");

                    return ResponseEntity.ok(result);
                } else {
                    return ResponseEntity.status(500)
                            .body(createErrorResponse("Full dashboard data not available", null));
                }
            } catch (Exception e) {
                log.error("API: Error getting full dashboard data", e);
                return ResponseEntity.status(500)
                        .body(createErrorResponse("Failed to get full dashboard data", e));
            }
        });
    }

    /**
     * Обновление кэша
     */
    @PostMapping(value = "/refresh-cache", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> refreshCacheApi(HttpServletRequest request) {
        try {
            log.info("API: Refreshing cache");

            // Аутентификация
            if (!validateApiAuthentication(request)) {
                return ResponseEntity.status(401)
                        .body(createErrorResponse("Unauthorized access", null));
            }

            Map<String, Object> result = refreshCache();

            // Логирование действия
            logAdminActivity(request, "API_CACHE_REFRESH",
                    "Обновление кэша через API");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("API: Error refreshing cache", e);
            return ResponseEntity.status(500)
                    .body(createErrorResponse("Failed to refresh cache", e));
        }
    }

    // ==================== РЕАЛИЗАЦИЯ AdminDashboardOperations ====================

    @Override
    public AdminDashboardService.DashboardOverview getDashboardOverview() {
        try {
            return adminDashboardService.getDashboardOverview();
        } catch (Exception e) {
            log.error("Error getting dashboard overview", e);
            return null;
        }
    }

    @Override
    public shit.back.dto.monitoring.SystemHealth getSystemHealth() {
        try {
            return adminDashboardService.getSystemHealth();
        } catch (Exception e) {
            log.error("Error getting system health", e);
            return null;
        }
    }

    @Override
    public AdminDashboardService.RecentActivity getRecentActivity() {
        try {
            return adminDashboardService.getRecentActivity();
        } catch (Exception e) {
            log.error("Error getting recent activity", e);
            return null;
        }
    }

    @Override
    public PerformanceMetrics getPerformanceMetrics() {
        try {
            return adminDashboardCacheService.getPerformanceMetricsCached();
        } catch (Exception e) {
            log.error("Error getting performance metrics", e);
            return null;
        }
    }

    @Override
    public Map<String, Object> refreshCache() {
        try {
            adminDashboardCacheService.clearAllCache();
            return createSuccessResponse("Cache refreshed successfully", null);
        } catch (Exception e) {
            log.error("Error refreshing cache", e);
            return createErrorResponse("Failed to refresh cache", e);
        }
    }

    @Override
    public Map<String, Object> getQuickStats() {
        try {
            Map<String, Object> stats = new java.util.HashMap<>();
            AdminDashboardService.DashboardOverview overview = getDashboardOverview();
            if (overview != null) {
                // Используем рефлексию для доступа к полям
                try {
                    java.lang.reflect.Field totalUsersField = overview.getClass()
                            .getDeclaredField("totalUsersCount");
                    totalUsersField.setAccessible(true);
                    stats.put("totalUsersCount", totalUsersField.get(overview));

                    java.lang.reflect.Field activeUsersField = overview.getClass()
                            .getDeclaredField("activeUsersCount");
                    activeUsersField.setAccessible(true);
                    stats.put("activeUsersCount", activeUsersField.get(overview));

                    java.lang.reflect.Field onlineUsersField = overview.getClass()
                            .getDeclaredField("onlineUsersCount");
                    onlineUsersField.setAccessible(true);
                    stats.put("onlineUsersCount", onlineUsersField.get(overview));
                } catch (Exception reflectionEx) {
                    log.warn("Failed to extract user stats via reflection", reflectionEx);
                    setDefaultStats(stats);
                }
            } else {
                setDefaultStats(stats);
            }
            stats.put("timestamp", LocalDateTime.now());
            return stats;
        } catch (Exception e) {
            log.error("Error getting quick stats", e);
            Map<String, Object> errorStats = new java.util.HashMap<>();
            setDefaultStats(errorStats);
            return errorStats;
        }
    }

    @Override
    public UserActivityStatisticsService.ActivityStatistics getActivityStatistics(int hours) {
        try {
            return userActivityLogService.getActivityStatistics(hours);
        } catch (Exception e) {
            log.error("Error getting activity statistics for {} hours", hours, e);
            return null;
        }
    }

    @Override
    public UserActivityStatisticsService.PaymentStatusDashboard getPaymentStatusDashboard() {
        try {
            return userActivityLogService.getPaymentStatusDashboard();
        } catch (Exception e) {
            log.error("Error getting payment status dashboard", e);
            return null;
        }
    }

    @Override
    public AdminDashboardCacheService.FullDashboardDataCached getFullDashboardData() {
        try {
            return adminDashboardCacheService.getFullDashboardDataCached();
        } catch (Exception e) {
            log.error("Error getting full dashboard data", e);
            return null;
        }
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    private void setDefaultStats(Map<String, Object> stats) {
        stats.put("totalUsersCount", 0L);
        stats.put("activeUsersCount", 0L);
        stats.put("onlineUsersCount", 0L);
        stats.put("dataLoaded", false);
        stats.put("lastUpdated", LocalDateTime.now());
    }
}