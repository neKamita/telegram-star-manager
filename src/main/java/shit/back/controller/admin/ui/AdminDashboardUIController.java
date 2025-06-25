package shit.back.controller.admin.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import shit.back.controller.admin.shared.AdminControllerOperations;
import shit.back.entity.OrderEntity;
import shit.back.entity.StarPackageEntity;
import shit.back.entity.UserSessionEntity;
import shit.back.entity.UserActivityLogEntity;
import shit.back.entity.UserActivityLogEntity.ActionType;
import shit.back.service.AdminDashboardService;
import shit.back.service.AdminDashboardCacheService;
import shit.back.service.OrderService;
import shit.back.service.StarPackageService;
import shit.back.service.UserSessionUnifiedService;
import shit.back.service.UserActivityLogService;
import shit.back.service.admin.shared.AdminAuthenticationService;
import shit.back.service.admin.shared.AdminSecurityHelper;

// Импорт корректных DTO
import shit.back.dto.order.OrderStatistics;
import shit.back.dto.monitoring.SystemHealth;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * UI контроллер для дашборда админ панели
 * Содержит только HTML представления для дашборда
 * Следует принципам SOLID и чистой архитектуры
 */
@Controller
@RequestMapping("/admin")
public class AdminDashboardUIController implements AdminControllerOperations {

    private static final Logger log = LoggerFactory.getLogger(AdminDashboardUIController.class);

    @Autowired
    private AdminDashboardService adminDashboardService;

    @Autowired
    private AdminDashboardCacheService adminDashboardCacheService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private StarPackageService starPackageService;

    @Autowired
    private UserSessionUnifiedService userSessionService;

    @Autowired
    private UserActivityLogService activityLogService;

    @Autowired
    private AdminAuthenticationService adminAuthenticationService;

    @Autowired
    private AdminSecurityHelper adminSecurityHelper;

    /**
     * Главная страница админ панели - объединяет логику дашборда
     */
    @GetMapping
    public String adminDashboard(Model model, HttpServletRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("Loading unified admin dashboard");

            // Проверка аутентификации через унифицированный сервис
            if (!adminAuthenticationService.validateAuthentication(request)) {
                log.warn("Unauthorized access attempt to dashboard from IP: {}",
                        adminSecurityHelper.getClientIpAddress(request));
                return "redirect:/admin/login";
            }

            // Проверка подозрительной активности
            if (adminSecurityHelper.isSuspiciousActivity(request)) {
                log.warn("Suspicious activity detected on dashboard");
                model.addAttribute("warning", "Подозрительная активность обнаружена");
            }

            model.addAttribute("title", "Admin Dashboard");
            model.addAttribute("subtitle", "Unified Management System");

            // Получение основных данных dashboard
            try {
                AdminDashboardService.DashboardOverview overview = adminDashboardService.getDashboardOverview();
                if (overview != null) {
                    model.addAttribute("overview", overview);
                    extractUserCountsFromOverview(model, overview);
                } else {
                    setDefaultUserCounts(model);
                }
            } catch (Exception overviewEx) {
                log.warn("Failed to get overview, setting defaults", overviewEx);
                setDefaultUserCounts(model);
            }

            // Добавляем данные о заказах
            try {
                List<OrderEntity> recentOrders = orderService.getRecentOrders(5);
                OrderStatistics orderStats = orderService.getOrderStatistics();

                model.addAttribute("recentOrders", recentOrders);
                model.addAttribute("orderStats", orderStats);

                // Извлечение статистик из orderStats
                if (orderStats != null) {
                    // Используем рефлексию для доступа к полям
                    try {
                        java.lang.reflect.Field totalOrdersField = orderStats.getClass()
                                .getDeclaredField("totalOrders");
                        totalOrdersField.setAccessible(true);
                        model.addAttribute("totalOrders", totalOrdersField.get(orderStats));

                        java.lang.reflect.Field completedOrdersField = orderStats.getClass()
                                .getDeclaredField("completedOrders");
                        completedOrdersField.setAccessible(true);
                        model.addAttribute("completedOrdersCount", completedOrdersField.get(orderStats));
                    } catch (Exception reflectionEx) {
                        log.warn("Failed to extract order stats via reflection", reflectionEx);
                        model.addAttribute("totalOrders", 0L);
                        model.addAttribute("completedOrdersCount", 0L);
                    }
                } else {
                    model.addAttribute("totalOrders", 0L);
                    model.addAttribute("completedOrdersCount", 0L);
                }
            } catch (Exception ordersEx) {
                log.warn("Failed to load orders data, using empty defaults", ordersEx);
                model.addAttribute("recentOrders", java.util.Collections.emptyList());
                model.addAttribute("orderStats", createEmptyOrderStats());
                model.addAttribute("totalOrders", 0L);
                model.addAttribute("completedOrdersCount", 0L);
            }

            // Получение информации о здоровье системы
            try {
                SystemHealth systemHealth = adminDashboardService.getSystemHealth();
                model.addAttribute("systemHealth", systemHealth != null ? systemHealth : createEmptySystemHealth());
            } catch (Exception healthEx) {
                log.warn("Failed to get system health", healthEx);
                model.addAttribute("systemHealth", createEmptySystemHealth());
            }

            // Добавление контекста безопасности
            Map<String, Object> securityContext = adminSecurityHelper.createSecurityContext(request);
            model.addAttribute("securityContext", securityContext);

            // Поддержка прогрессивной загрузки
            model.addAttribute("needsProgressiveLoading", true);
            model.addAttribute("dataComplete", true);
            model.addAttribute("ultraLightweight", false);
            model.addAttribute("lastUpdated", LocalDateTime.now());

            // Информация о производительности
            long loadTime = System.currentTimeMillis() - startTime;
            model.addAttribute("loadTime", loadTime);
            log.info("Unified admin dashboard loaded in {}ms", loadTime);

            // Логирование просмотра через security helper
            adminSecurityHelper.logAdminActivity(request, "VIEW_DASHBOARD",
                    "Просмотр главной страницы дашборда");

            return "admin/dashboard";

        } catch (Exception e) {
            log.error("Error loading unified admin dashboard", e);
            model.addAttribute("error", "Ошибка загрузки панели администратора: " + e.getMessage());
            return "admin/error";
        }
    }

    /**
     * Страница управления пользователями
     */
    @GetMapping("/users")
    public String usersPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "lastActivity") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String search,
            Model model,
            HttpServletRequest request) {

        try {
            log.info("Loading users management page");

            // Проверка аутентификации
            if (!adminAuthenticationService.validateAuthentication(request)) {
                return "redirect:/admin/login";
            }

            // Создание параметров сортировки
            Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
            Pageable pageable = PageRequest.of(page, size, sort);

            Page<UserSessionEntity> users = adminDashboardService.getUsersWithSearch(search, pageable);
            UserSessionUnifiedService.UserSessionStatistics userStats = userSessionService.getUserSessionStatistics();

            model.addAttribute("title", "Users Management");
            model.addAttribute("subtitle", "Управление пользователями системы");
            model.addAttribute("users", users);
            model.addAttribute("userStats", userStats);
            model.addAttribute("search", search);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", users.getTotalPages());
            model.addAttribute("sortBy", sortBy);
            model.addAttribute("sortDir", sortDir);

            // Логирование просмотра
            adminSecurityHelper.logAdminActivity(request, "VIEW_USERS",
                    "Просмотр страницы управления пользователями");

            return "admin/users";

        } catch (Exception e) {
            log.error("Error loading users page", e);
            model.addAttribute("error", "Ошибка загрузки пользователей: " + e.getMessage());
            return "admin/error";
        }
    }

    /**
     * Страница управления пакетами
     */
    @GetMapping("/packages")
    public String packagesPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "sortOrder") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            Model model,
            HttpServletRequest request) {

        try {
            log.info("Loading packages management page");

            // Проверка аутентификации
            if (!adminAuthenticationService.validateAuthentication(request)) {
                return "redirect:/admin/login";
            }

            Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
            Pageable pageable = PageRequest.of(page, size, sort);

            Page<StarPackageEntity> packages = adminDashboardService.getPackages(pageable);
            StarPackageService.PackageStatistics packageStats = starPackageService.getPackageStatistics();

            model.addAttribute("title", "Packages Management");
            model.addAttribute("subtitle", "Управление пакетами звезд");
            model.addAttribute("packages", packages);
            model.addAttribute("packageStats", packageStats);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", packages.getTotalPages());
            model.addAttribute("sortBy", sortBy);
            model.addAttribute("sortDir", sortDir);

            // Логирование просмотра
            adminSecurityHelper.logAdminActivity(request, "VIEW_PACKAGES",
                    "Просмотр страницы управления пакетами");

            return "admin/packages";

        } catch (Exception e) {
            log.error("Error loading packages page", e);
            model.addAttribute("error", "Ошибка загрузки пакетов: " + e.getMessage());
            return "admin/error";
        }
    }

    /**
     * Страница аналитики
     */
    @GetMapping("/analytics")
    public String analyticsPage(
            @RequestParam(defaultValue = "30") int days,
            Model model,
            HttpServletRequest request) {

        try {
            log.info("Loading analytics page for {} days", days);

            // Проверка аутентификации
            if (!adminAuthenticationService.validateAuthentication(request)) {
                return "redirect:/admin/login";
            }

            // TODO: Реализовать DTO для AnalyticsData и TopPerformers, если потребуется
            Object analytics = adminDashboardService.getAnalyticsData(days);
            Object topPerformers = adminDashboardService.getTopPerformers();

            model.addAttribute("title", "Analytics");
            model.addAttribute("subtitle", "Аналитика и статистика системы");
            model.addAttribute("analytics", analytics);
            model.addAttribute("topPerformers", topPerformers);
            model.addAttribute("days", days);

            // Логирование просмотра
            adminSecurityHelper.logAdminActivity(request, "VIEW_ANALYTICS",
                    "Просмотр аналитики за " + days + " дней");

            return "admin/analytics";

        } catch (Exception e) {
            log.error("Error loading analytics page", e);
            model.addAttribute("error", "Ошибка загрузки аналитики: " + e.getMessage());
            return "admin/error";
        }
    }

    /**
     * Страница мониторинга системы
     */
    @GetMapping("/monitoring")
    public String monitoringPage(Model model, HttpServletRequest request) {
        try {
            log.info("Loading system monitoring page");

            // Проверка аутентификации
            if (!adminAuthenticationService.validateAuthentication(request)) {
                return "redirect:/admin/login";
            }

            model.addAttribute("title", "System Monitoring");
            model.addAttribute("subtitle", "System Health & Performance Monitoring");
            model.addAttribute("pageSection", "monitoring");

            SystemHealth systemHealth = adminDashboardService.getSystemHealth();
            if (systemHealth != null) {
                model.addAttribute("systemHealth", systemHealth);
            } else {
                model.addAttribute("systemHealth", createEmptySystemHealth());
                model.addAttribute("warning", "System health data temporarily unavailable");
            }

            // Поддержка прогрессивной загрузки
            model.addAttribute("progressiveLoading", true);
            model.addAttribute("fastLoadMode", true);

            // Логирование просмотра
            adminSecurityHelper.logAdminActivity(request, "VIEW_MONITORING",
                    "Просмотр страницы мониторинга системы");

            return "admin/monitoring";

        } catch (Exception e) {
            log.error("Error loading monitoring page", e);
            model.addAttribute("error", "Ошибка загрузки страницы мониторинга: " + e.getMessage());
            return "admin/error";
        }
    }

    /**
     * Страница логов активности
     */
    @GetMapping("/activity-logs")
    public String activityLogsPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(defaultValue = "false") boolean showAll,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<ActionType> actionTypes,
            Model model,
            HttpServletRequest request) {

        try {
            log.info("Loading activity logs page - showAll: {}, search: {}", showAll, search);

            // Проверка аутентификации
            if (!adminAuthenticationService.validateAuthentication(request)) {
                return "redirect:/admin/login";
            }

            Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());

            Page<UserActivityLogEntity> activities = activityLogService.getActivitiesWithFilters(
                    showAll, null, null, actionTypes, search, pageable);

            List<UserActivityLogEntity> recentActivities = activityLogService.getRecentActivities(1);

            UserActivityLogService.PaymentStatusDashboard paymentDashboard = activityLogService
                    .getPaymentStatusDashboard();

            UserActivityLogService.ActivityStatistics stats = activityLogService.getActivityStatistics(24);

            model.addAttribute("title", "Activity Logs");
            model.addAttribute("subtitle", "Real-time User Activity & Payment Status Dashboard");
            model.addAttribute("pageSection", "activity-logs");
            model.addAttribute("activities", activities);
            model.addAttribute("recentActivities", recentActivities);
            model.addAttribute("paymentDashboard",
                    paymentDashboard != null ? paymentDashboard : createEmptyPaymentDashboard());
            model.addAttribute("activityStats", stats != null ? stats : createEmptyActivityStats());
            model.addAttribute("showAll", showAll);
            model.addAttribute("search", search);
            model.addAttribute("actionTypes", ActionType.values());
            model.addAttribute("selectedActionTypes", actionTypes);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", activities.getTotalPages());

            // Поддержка прогрессивной загрузки
            model.addAttribute("logsCount", activities.getTotalElements());
            model.addAttribute("progressiveLoading", true);

            // Логирование просмотра
            adminSecurityHelper.logAdminActivity(request, "VIEW_ACTIVITY_LOGS",
                    "Просмотр логов активности, фильтры: showAll=" + showAll +
                            ", search=" + (search != null ? search : "none"));

            return "admin/activity-logs";

        } catch (Exception e) {
            log.error("Error loading activity logs page", e);
            model.addAttribute("error", "Ошибка загрузки логов активности: " + e.getMessage());
            return "admin/error";
        }
    }

    /**
     * Обновление кэша (UI форма)
     */
    @PostMapping("/refresh-cache")
    public String refreshCache(RedirectAttributes redirectAttributes, HttpServletRequest request) {
        try {
            // Проверка аутентификации
            if (!adminAuthenticationService.validateAuthentication(request)) {
                return "redirect:/admin/login";
            }

            adminDashboardCacheService.clearAllCache();
            redirectAttributes.addFlashAttribute("success", "Cache refreshed successfully");

            // Логирование действия
            adminSecurityHelper.logAdminActivity(request, "REFRESH_CACHE",
                    "Обновление кэша через UI форму");

            return "redirect:/admin";

        } catch (Exception e) {
            log.error("Error refreshing cache", e);
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin";
        }
    }

    // Utility методы

    private void extractUserCountsFromOverview(Model model, AdminDashboardService.DashboardOverview overview) {
        try {
            // Используем рефлексию для доступа к полям
            java.lang.reflect.Field totalUsersField = overview.getClass().getDeclaredField("totalUsersCount");
            totalUsersField.setAccessible(true);
            model.addAttribute("totalUsersCount", totalUsersField.get(overview));

            java.lang.reflect.Field activeUsersField = overview.getClass().getDeclaredField("activeUsersCount");
            activeUsersField.setAccessible(true);
            model.addAttribute("activeUsersCount", activeUsersField.get(overview));

            java.lang.reflect.Field onlineUsersField = overview.getClass().getDeclaredField("onlineUsersCount");
            onlineUsersField.setAccessible(true);
            model.addAttribute("onlineUsersCount", onlineUsersField.get(overview));
        } catch (Exception e) {
            log.warn("Failed to extract user counts from overview", e);
            setDefaultUserCounts(model);
        }
    }

    private void setDefaultUserCounts(Model model) {
        model.addAttribute("totalUsersCount", 0L);
        model.addAttribute("activeUsersCount", 0L);
        model.addAttribute("onlineUsersCount", 0L);
    }

    private Object createEmptySystemHealth() {
        return java.util.Map.of(
                "healthScore", 0,
                "lastChecked", LocalDateTime.now(),
                "redisHealthy", false,
                "botHealthy", false,
                "cacheHealthy", false,
                "totalUsers", 0L,
                "activeUsers", 0L,
                "onlineUsers", 0L,
                "totalOrders", 0L);
    }

    private Object createEmptyPaymentDashboard() {
        return java.util.Map.of(
                "completedPayments", java.util.Collections.emptyList(),
                "pendingPayments", java.util.Collections.emptyList(),
                "failedPayments", java.util.Collections.emptyList(),
                "cancelledOrders", java.util.Collections.emptyList(),
                "stuckUsers", java.util.Collections.emptyList());
    }

    private Object createEmptyActivityStats() {
        return java.util.Map.of(
                "totalActivities", 0,
                "keyActivities", 0,
                "periodHours", 24);
    }

    private Object createEmptyOrderStats() {
        return java.util.Map.of(
                "totalOrders", 0L,
                "completedOrders", 0L,
                "pendingOrders", 0L,
                "failedOrders", 0L,
                "totalRevenue", java.math.BigDecimal.ZERO,
                "conversionRate", 0.0);
    }

    @Override
    public Map<String, Object> createErrorResponse(String message, Exception e) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        response.put("message", e != null ? e.getMessage() : "Unknown error");
        response.put("timestamp", LocalDateTime.now());
        return response;
    }
}
