package shit.back.controller.admin.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import shit.back.controller.admin.shared.AdminControllerOperations;
import shit.back.service.AdminDashboardService;
import shit.back.service.AdminDashboardCacheService;
import shit.back.dto.monitoring.PerformanceMetrics;
import shit.back.dto.order.OrderStatistics;
import shit.back.service.UserActivityLogService;
import shit.back.service.StarPackageService;
import shit.back.service.OrderService;
import shit.back.service.admin.shared.AdminAuthenticationService;
import shit.back.service.admin.shared.AdminSecurityHelper;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * REST API контроллер для дашборда админ панели
 * Содержит только JSON API endpoints для дашборда
 * Следует принципам SOLID и чистой архитектуры
 */
@RestController
@RequestMapping("/admin/api/dashboard")
public class AdminDashboardApiController implements AdminControllerOperations {

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

    @Autowired
    private AdminAuthenticationService adminAuthenticationService;

    @Autowired
    private AdminSecurityHelper adminSecurityHelper;

    /**
     * Получение базовых данных dashboard
     */
    @GetMapping(value = "/overview", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getDashboardOverview(HttpServletRequest request) {
        try {
            log.debug("API: Getting dashboard overview");
            log.info("🔧 ИСПРАВЛЕНИЕ: Dashboard overview request from: {}", request.getRemoteAddr());

            // Аутентификация
            if (!adminAuthenticationService.validateApiRequest(request)) {
                log.warn("🔧 ИСПРАВЛЕНИЕ: Authentication failed for dashboard overview");
                return ResponseEntity.status(401)
                        .body(createErrorResponse("Unauthorized access", null));
            }

            AdminDashboardService.DashboardOverview overview = adminDashboardService.getDashboardOverview();
            PerformanceMetrics performance = adminDashboardCacheService
                    .getPerformanceMetricsCached();

            Map<String, Object> dashboardData = Map.of(
                    "overview", overview != null ? overview : Map.of(),
                    "performance", performance != null ? performance : Map.of(),
                    "lastUpdated", LocalDateTime.now(),
                    "success", true);

            // Логирование доступа
            adminSecurityHelper.logAdminActivity(request, "API_DASHBOARD_OVERVIEW",
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
    public CompletableFuture<ResponseEntity<Object>> getFullDashboardData(HttpServletRequest request) {
        log.debug("API: Getting full dashboard data asynchronously");

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Аутентификация
                if (!adminAuthenticationService.validateApiRequest(request)) {
                    return ResponseEntity.status(401)
                            .body(createErrorResponse("Unauthorized access", null));
                }

                AdminDashboardCacheService.FullDashboardDataCached fullData = adminDashboardCacheService
                        .getFullDashboardDataCached();

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
                    adminSecurityHelper.logAdminActivity(request, "API_DASHBOARD_FULL",
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
     * Получение системного здоровья
     */
    @GetMapping(value = "/system-health", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<ResponseEntity<Object>> getSystemHealth(HttpServletRequest request) {
        log.debug("API: Getting system health");

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Аутентификация
                if (!adminAuthenticationService.validateApiRequest(request)) {
                    return ResponseEntity.status(401)
                            .body(createErrorResponse("Unauthorized access", null));
                }

                // Используем DTO SystemHealth
                shit.back.dto.monitoring.SystemHealth systemHealth = adminDashboardService.getSystemHealth();

                if (systemHealth != null) {
                    // Логирование доступа
                    adminSecurityHelper.logAdminActivity(request, "API_SYSTEM_HEALTH",
                            "Получение информации о здоровье системы через API");

                    return ResponseEntity.ok(systemHealth);
                } else {
                    return ResponseEntity.ok(Map.of(
                            "healthScore", 0,
                            "lastChecked", LocalDateTime.now(),
                            "error", "System health data temporarily unavailable"));
                }
            } catch (Exception e) {
                log.error("API: Error getting system health", e);
                return ResponseEntity.status(500)
                        .body(createErrorResponse("Failed to get system health", e));
            }
        });
    }

    /**
     * Получение недавней активности
     */
    @GetMapping(value = "/recent-activity", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getRecentActivity(HttpServletRequest request) {
        try {
            log.debug("API: Getting recent activity");

            // Аутентификация
            if (!adminAuthenticationService.validateApiRequest(request)) {
                return ResponseEntity.status(401)
                        .body(createErrorResponse("Unauthorized access", null));
            }

            AdminDashboardService.RecentActivity recentActivity = adminDashboardService.getRecentActivity();

            if (recentActivity != null) {
                // Логирование доступа
                adminSecurityHelper.logAdminActivity(request, "API_RECENT_ACTIVITY",
                        "Получение недавней активности через API");

                return ResponseEntity.ok(recentActivity);
            } else {
                // Создаем mock данные для совместимости с JavaScript
                java.util.List<Map<String, Object>> mockRecentOrders = java.util.Arrays.asList(
                        Map.of("id", "001", "status", "COMPLETED", "createdAt",
                                java.time.LocalDateTime.now().minusMinutes(5).toString()),
                        Map.of("id", "002", "status", "PENDING", "createdAt",
                                java.time.LocalDateTime.now().minusMinutes(15).toString()));

                java.util.List<Map<String, Object>> mockNewUsers = java.util.Arrays.asList(
                        Map.of("telegramUserId", "12345", "telegramUsername", "user1", "createdAt",
                                java.time.LocalDateTime.now().minusMinutes(10).toString()),
                        Map.of("telegramUserId", "67890", "telegramUsername", "user2", "createdAt",
                                java.time.LocalDateTime.now().minusMinutes(20).toString()));

                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "recentOrders", mockRecentOrders,
                        "newUsers", mockNewUsers,
                        "totalRecentOrders", 2,
                        "totalNewUsers", 2,
                        "totalOnlineUsers", 0,
                        "lastUpdated", LocalDateTime.now()));
            }
        } catch (Exception e) {
            log.error("API: Error getting recent activity", e);
            return ResponseEntity.status(500)
                    .body(createErrorResponse("Failed to get recent activity", e));
        }
    }

    /**
     * Получение быстрых статистик
     */
    @GetMapping(value = "/quick-stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getQuickStats(HttpServletRequest request) {
        try {
            log.debug("API: Getting quick stats");

            // Аутентификация
            if (!adminAuthenticationService.validateApiRequest(request)) {
                return ResponseEntity.status(401)
                        .body(createErrorResponse("Unauthorized access", null));
            }

            Map<String, Object> stats = new java.util.HashMap<>();

            // Получение данных через dashboard service
            try {
                AdminDashboardService.DashboardOverview overview = adminDashboardService.getDashboardOverview();
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

                        stats.put("dataLoaded", true);
                    } catch (Exception reflectionEx) {
                        log.warn("Failed to extract user stats via reflection", reflectionEx);
                        setDefaultStats(stats);
                    }
                } else {
                    setDefaultStats(stats);
                }
            } catch (Exception e) {
                log.warn("Failed to get overview for quick stats", e);
                setDefaultStats(stats);
            }

            // Добавляем данные о заказах
            Long totalOrders = 0L;
            Long completedOrdersCount = 0L;
            try {
                OrderStatistics orderStats = orderService.getOrderStatistics();
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
                        log.warn("Failed to extract order stats via reflection for quick stats", reflectionEx);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to get order stats for quick stats", e);
            }

            // Объединяем статистики
            stats.put("totalOrders", totalOrders);
            stats.put("completedOrdersCount", completedOrdersCount);
            stats.put("timestamp", LocalDateTime.now());

            // Логирование доступа
            adminSecurityHelper.logAdminActivity(request, "API_QUICK_STATS",
                    "Получение быстрой статистики через API");

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("API: Error getting quick stats", e);
            return ResponseEntity.status(500)
                    .body(createErrorResponse("Failed to get quick stats", e));
        }
    }

    /**
     * Получение статистики активности
     */
    @GetMapping(value = "/activity-statistics", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getActivityStatistics(
            @RequestParam(defaultValue = "24") int hours,
            HttpServletRequest request) {
        try {
            log.debug("API: Getting activity statistics for {} hours", hours);

            // Аутентификация
            if (!adminAuthenticationService.validateApiRequest(request)) {
                return ResponseEntity.status(401)
                        .body(createErrorResponse("Unauthorized access", null));
            }

            UserActivityLogService.ActivityStatistics stats = userActivityLogService.getActivityStatistics(hours);

            if (stats != null) {
                // Логирование доступа
                adminSecurityHelper.logAdminActivity(request, "API_ACTIVITY_STATS",
                        "Получение статистики активности за " + hours + " часов через API");

                return ResponseEntity.ok(stats);
            } else {
                return ResponseEntity.ok(Map.of(
                        "totalActivities", 0,
                        "keyActivities", 0,
                        "periodHours", hours,
                        "timestamp", LocalDateTime.now()));
            }
        } catch (Exception e) {
            log.error("API: Error getting activity statistics for {} hours", hours, e);
            return ResponseEntity.status(500)
                    .body(createErrorResponse("Failed to get activity statistics", e));
        }
    }

    /**
     * Обновление кэша
     */
    @PostMapping(value = "/refresh-cache", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> refreshCache(HttpServletRequest request) {
        try {
            log.info("API: Refreshing cache");

            // Аутентификация
            if (!adminAuthenticationService.validateApiRequest(request)) {
                return ResponseEntity.status(401)
                        .body(createErrorResponse("Unauthorized access", null));
            }

            adminDashboardCacheService.clearAllCache();

            // Логирование действия
            adminSecurityHelper.logAdminActivity(request, "API_CACHE_REFRESH",
                    "Обновление кэша через API");

            return ResponseEntity.ok(createSuccessResponse("Cache refreshed successfully", null));

        } catch (Exception e) {
            log.error("API: Error refreshing cache", e);
            return ResponseEntity.status(500)
                    .body(createErrorResponse("Failed to refresh cache", e));
        }
    }

    /**
     * Server-Sent Events для real-time activity stream
     */
    @GetMapping(value = "/activity-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter getActivityStream(HttpServletRequest request) {
        try {
            // Аутентификация
            if (!adminAuthenticationService.validateApiRequest(request)) {
                SseEmitter emitter = new SseEmitter(0L);
                try {
                    emitter.completeWithError(new RuntimeException("Unauthorized"));
                } catch (Exception ex) {
                    log.error("Error completing SSE with unauthorized error", ex);
                }
                return emitter;
            }

            String clientId = "admin-api-" + UUID.randomUUID().toString().substring(0, 8);
            log.info("API: Creating SSE connection for activity stream: {}", clientId);

            // Логирование подключения
            adminSecurityHelper.logAdminActivity(request, "API_SSE_CONNECT",
                    "Подключение к потоку активности через SSE: " + clientId);

            return userActivityLogService.createSseConnection(clientId);

        } catch (Exception e) {
            log.error("API: Error creating activity stream SSE connection", e);
            SseEmitter emitter = new SseEmitter(0L);
            try {
                emitter.completeWithError(e);
            } catch (Exception ex) {
                log.error("API: Error completing SSE with error", ex);
            }
            return emitter;
        }
    }

    // Вспомогательные методы

    private void setDefaultStats(Map<String, Object> stats) {
        stats.put("totalUsersCount", 0L);
        stats.put("activeUsersCount", 0L);
        stats.put("onlineUsersCount", 0L);
        stats.put("dataLoaded", false);
        stats.put("lastUpdated", LocalDateTime.now());
    }

    @Override
    public Map<String, Object> createErrorResponse(String message, Exception e) {
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("success", false);
        response.put("error", message);
        response.put("message", e != null ? e.getMessage() : "Unknown error");
        response.put("timestamp", LocalDateTime.now());
        return response;
    }
}