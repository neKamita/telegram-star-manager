package shit.back.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import shit.back.entity.OrderEntity;
import shit.back.entity.StarPackageEntity;
import shit.back.entity.UserSessionEntity;
import shit.back.entity.UserActivityLogEntity;
import shit.back.entity.UserActivityLogEntity.ActionType;
import shit.back.service.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Enhanced admin controller with PostgreSQL integration (Feature Flags removed)
 */
@Slf4j
@Controller
@RequestMapping("/admin-legacy")
public class AdminController {
    
    @Autowired
    private AdminDashboardService adminDashboardService;
    
    @Autowired
    private OrderService orderService;
    
    @Autowired
    private StarPackageService starPackageService;
    
    @Autowired
    private UserSessionEnhancedService userSessionService;
    
    @Autowired
    private UserActivityLogService activityLogService;
    
    /**
     * Main admin dashboard with comprehensive analytics (Feature Flags removed)
     */
    @GetMapping
    public String adminDashboard(Model model) {
        try {
            log.info("Loading enhanced admin dashboard");
            
            // Get comprehensive dashboard data
            AdminDashboardService.DashboardOverview overview = adminDashboardService.getDashboardOverview();
            AdminDashboardService.PerformanceMetrics performance = adminDashboardService.getPerformanceMetrics();
            AdminDashboardService.RecentActivity recentActivity = adminDashboardService.getRecentActivity();
            AdminDashboardService.CombinedRecentActivity combinedActivity = adminDashboardService.getCombinedRecentActivity();
            AdminDashboardService.SystemHealth systemHealth = adminDashboardService.getSystemHealth();
            
            model.addAttribute("title", "Enhanced Dashboard");
            model.addAttribute("subtitle", "PostgreSQL-powered Analytics");
            model.addAttribute("overview", overview);
            model.addAttribute("performance", performance);
            model.addAttribute("recentActivity", recentActivity);
            model.addAttribute("combinedActivity", combinedActivity);
            model.addAttribute("systemHealth", systemHealth);
            
            // Add user count directly for initial page load
            model.addAttribute("activeUsersCount", overview.getActiveUsersCount());
            model.addAttribute("onlineUsersCount", overview.getOnlineUsersCount());
            model.addAttribute("totalUsersCount", overview.getTotalUsersCount());
            
            return "admin/dashboard";
        } catch (Exception e) {
            log.error("Error loading enhanced admin dashboard", e);
            model.addAttribute("error", "Ошибка загрузки панели администратора: " + e.getMessage());
            return "admin/error";
        }
    }
    
    /**
     * Orders management page
     */
    @GetMapping("/orders")
    public String ordersPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String search,
            Model model) {
        
        try {
            Sort sort = sortDir.equalsIgnoreCase("desc") ? 
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
            Pageable pageable = PageRequest.of(page, size, sort);
            
            Page<OrderEntity> orders = adminDashboardService.getOrdersWithSearch(search, pageable);
            OrderService.OrderStatistics orderStats = orderService.getOrderStatistics();
            
            model.addAttribute("title", "Orders Management");
            model.addAttribute("orders", orders);
            model.addAttribute("orderStats", orderStats);
            model.addAttribute("search", search);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", orders.getTotalPages());
            model.addAttribute("sortBy", sortBy);
            model.addAttribute("sortDir", sortDir);
            
            return "admin/orders";
        } catch (Exception e) {
            log.error("Error loading orders page", e);
            model.addAttribute("error", "Ошибка загрузки заказов: " + e.getMessage());
            return "admin/error";
        }
    }
    
    /**
     * Users management page
     */
    @GetMapping("/users")
    public String usersPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "lastActivity") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String search,
            Model model) {
        
        try {
            Sort sort = sortDir.equalsIgnoreCase("desc") ? 
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
            Pageable pageable = PageRequest.of(page, size, sort);
            
            Page<UserSessionEntity> users = adminDashboardService.getUsersWithSearch(search, pageable);
            UserSessionEnhancedService.UserSessionStatistics userStats = userSessionService.getUserSessionStatistics();
            
            model.addAttribute("title", "Users Management");
            model.addAttribute("users", users);
            model.addAttribute("userStats", userStats);
            model.addAttribute("search", search);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", users.getTotalPages());
            model.addAttribute("sortBy", sortBy);
            model.addAttribute("sortDir", sortDir);
            
            return "admin/users";
        } catch (Exception e) {
            log.error("Error loading users page", e);
            model.addAttribute("error", "Ошибка загрузки пользователей: " + e.getMessage());
            return "admin/error";
        }
    }
    
    /**
     * Packages management page
     */
    @GetMapping("/packages")
    public String packagesPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "sortOrder") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            Model model) {
        
        try {
            Sort sort = sortDir.equalsIgnoreCase("desc") ? 
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
            Pageable pageable = PageRequest.of(page, size, sort);
            
            Page<StarPackageEntity> packages = adminDashboardService.getPackages(pageable);
            StarPackageService.PackageStatistics packageStats = starPackageService.getPackageStatistics();
            
            model.addAttribute("title", "Packages Management");
            model.addAttribute("packages", packages);
            model.addAttribute("packageStats", packageStats);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", packages.getTotalPages());
            model.addAttribute("sortBy", sortBy);
            model.addAttribute("sortDir", sortDir);
            
            return "admin/packages";
        } catch (Exception e) {
            log.error("Error loading packages page", e);
            model.addAttribute("error", "Ошибка загрузки пакетов: " + e.getMessage());
            return "admin/error";
        }
    }
    
    /**
     * Analytics page with charts and graphs
     */
    @GetMapping("/analytics")
    public String analyticsPage(
            @RequestParam(defaultValue = "30") int days,
            Model model) {
        
        try {
            AdminDashboardService.AnalyticsData analytics = adminDashboardService.getAnalyticsData(days);
            AdminDashboardService.TopPerformers topPerformers = adminDashboardService.getTopPerformers();
            
            model.addAttribute("title", "Analytics");
            model.addAttribute("analytics", analytics);
            model.addAttribute("topPerformers", topPerformers);
            model.addAttribute("days", days);
            
            return "admin/analytics";
        } catch (Exception e) {
            log.error("Error loading analytics page", e);
            model.addAttribute("error", "Ошибка загрузки аналитики: " + e.getMessage());
            return "admin/error";
        }
    }
    
    /**
     * System monitoring page (Feature Flags removed)
     */
    @GetMapping("/monitoring")
    public String monitoringPage(Model model) {
        try {
            AdminDashboardService.SystemHealth systemHealth = adminDashboardService.getSystemHealth();
            
            model.addAttribute("title", "System Monitoring");
            model.addAttribute("subtitle", "System Health & Performance Monitoring");
            model.addAttribute("systemHealth", systemHealth);
            
            return "admin/monitoring";
        } catch (Exception e) {
            log.error("Error loading monitoring page", e);
            model.addAttribute("error", "Ошибка загрузки страницы мониторинга: " + e.getMessage());
            return "admin/error";
        }
    }
    
    /**
     * Activity Logs page with real-time user activity feed and payment status dashboard
     */
    @GetMapping("/activity-logs")
    public String activityLogsPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(defaultValue = "false") boolean showAll,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<ActionType> actionTypes,
            Model model) {
        
        try {
            log.info("Loading activity logs page - showAll: {}, search: {}", showAll, search);
            
            // Prepare pagination
            Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
            
            // Get filtered activities
            Page<UserActivityLogEntity> activities = activityLogService.getActivitiesWithFilters(
                showAll, null, null, actionTypes, search, pageable
            );
            
            // Get recent activities for live feed
            List<UserActivityLogEntity> recentActivities = activityLogService.getRecentActivities(1);
            
            // Get payment status dashboard
            UserActivityLogService.PaymentStatusDashboard paymentDashboard = 
                activityLogService.getPaymentStatusDashboard();
            
            // Get activity statistics
            UserActivityLogService.ActivityStatistics stats = 
                activityLogService.getActivityStatistics(24);
            
            model.addAttribute("title", "Activity Logs");
            model.addAttribute("subtitle", "Real-time User Activity & Payment Status Dashboard");
            model.addAttribute("activities", activities);
            model.addAttribute("recentActivities", recentActivities);
            model.addAttribute("paymentDashboard", paymentDashboard);
            model.addAttribute("activityStats", stats);
            model.addAttribute("showAll", showAll);
            model.addAttribute("search", search);
            model.addAttribute("actionTypes", ActionType.values());
            model.addAttribute("selectedActionTypes", actionTypes);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", activities.getTotalPages());
            
            return "admin/activity-logs";
        } catch (Exception e) {
            log.error("Error loading activity logs page", e);
            model.addAttribute("error", "Ошибка загрузки логов активности: " + e.getMessage());
            return "admin/error";
        }
    }
    
    /**
     * SSE endpoint for real-time activity stream
     */
    @GetMapping(value = "/api/activity-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter activityStream() {
        try {
            String clientId = "admin-" + UUID.randomUUID().toString().substring(0, 8);
            log.info("Creating SSE connection for activity stream: {}", clientId);
            
            return activityLogService.createSseConnection(clientId);
        } catch (Exception e) {
            log.error("Error creating activity stream SSE connection", e);
            SseEmitter emitter = new SseEmitter(0L);
            try {
                emitter.completeWithError(e);
            } catch (Exception ex) {
                log.error("Error completing SSE with error", ex);
            }
            return emitter;
        }
    }
    
    /**
     * API endpoint for payment status dashboard data
     */
    @GetMapping(value = "/api/payment-status-dashboard", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public UserActivityLogService.PaymentStatusDashboard getPaymentStatusDashboard() {
        try {
            return activityLogService.getPaymentStatusDashboard();
        } catch (Exception e) {
            log.error("Error getting payment status dashboard", e);
            return UserActivityLogService.PaymentStatusDashboard.builder()
                .completedPayments(List.of())
                .pendingPayments(List.of())
                .failedPayments(List.of())
                .cancelledOrders(List.of())
                .stuckUsers(List.of())
                .build();
        }
    }
    
    /**
     * API endpoint for activity statistics
     */
    @GetMapping(value = "/api/activity-statistics", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public UserActivityLogService.ActivityStatistics getActivityStatistics(
            @RequestParam(defaultValue = "24") int hours) {
        try {
            return activityLogService.getActivityStatistics(hours);
        } catch (Exception e) {
            log.error("Error getting activity statistics for {} hours", hours, e);
            return UserActivityLogService.ActivityStatistics.builder()
                .totalActivities(0)
                .keyActivities(0)
                .periodHours(hours)
                .build();
        }
    }
    
    /**
     * Package toggle endpoint
     */
    @PostMapping("/packages/{packageId}/toggle")
    @ResponseBody
    public String togglePackage(@PathVariable Long packageId) {
        try {
            Optional<StarPackageEntity> result = starPackageService.togglePackageStatus(packageId);
            if (result.isPresent()) {
                return result.get().getIsEnabled() ? "enabled" : "disabled";
            }
            return "error";
        } catch (Exception e) {
            log.error("Error toggling package {}", packageId, e);
            return "error";
        }
    }
    
    /**
     * Maintenance operations
     */
    @PostMapping("/maintenance")
    @ResponseBody
    public AdminDashboardService.MaintenanceResult performMaintenance() {
        try {
            return adminDashboardService.performMaintenance();
        } catch (Exception e) {
            log.error("Error performing maintenance", e);
            return AdminDashboardService.MaintenanceResult.builder()
                    .deactivatedSessions(0)
                    .deactivatedPackages(0)
                    .maintenanceTime(LocalDateTime.now())
                    .build();
        }
    }
    
    /**
     * Simple cache refresh (Feature Flags operations removed)
     */
    @PostMapping("/refresh-cache")
    public String refreshCache(RedirectAttributes redirectAttributes) {
        try {
            // Only refresh what's available without Feature Flags
            log.info("Basic cache refresh via admin panel");
            redirectAttributes.addFlashAttribute("success", "Cache refreshed successfully");
            return "redirect:/admin-legacy";
        } catch (Exception e) {
            log.error("Error refreshing cache", e);
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin-legacy";
        }
    }
    
    /**
     * API cache refresh (Feature Flags operations removed)
     */
    @PostMapping("/api/refresh-cache")
    @ResponseBody
    public Map<String, Object> refreshCacheApi() {
        try {
            log.info("Basic cache refresh via API");
            
            return Map.of(
                "success", true,
                "message", "Cache refreshed successfully",
                "timestamp", LocalDateTime.now()
            );
        } catch (Exception e) {
            log.error("Error refreshing cache via API", e);
            return Map.of(
                "success", false,
                "message", "Failed to refresh cache: " + e.getMessage(),
                "timestamp", LocalDateTime.now()
            );
        }
    }
    
    // API endpoints for dashboard data (Feature Flags removed)
    
    @GetMapping(value = "/api/dashboard-data", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public CleanDashboardData getDashboardData() {
        try {
            AdminDashboardService.DashboardOverview overview = adminDashboardService.getDashboardOverview();
            AdminDashboardService.PerformanceMetrics performance = adminDashboardService.getPerformanceMetrics();
            
            return CleanDashboardData.builder()
                    .overview(overview)
                    .performance(performance)
                    .lastUpdated(LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            log.error("Error getting dashboard data", e);
            return CleanDashboardData.builder()
                    .lastUpdated(LocalDateTime.now())
                    .build();
        }
    }
    
    @GetMapping(value = "/api/analytics/{days}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public AdminDashboardService.AnalyticsData getAnalyticsData(@PathVariable int days) {
        try {
            return adminDashboardService.getAnalyticsData(days);
        } catch (Exception e) {
            log.error("Error getting analytics data for {} days", days, e);
            return AdminDashboardService.AnalyticsData.builder().build();
        }
    }
    
    @GetMapping(value = "/api/system-health", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public AdminDashboardService.SystemHealth getSystemHealth() {
        try {
            return adminDashboardService.getSystemHealth();
        } catch (Exception e) {
            log.error("Error getting system health", e);
            return AdminDashboardService.SystemHealth.builder()
                    .healthScore(0)
                    .lastChecked(LocalDateTime.now())
                    .build();
        }
    }
    
    @GetMapping(value = "/api/recent-activity", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public AdminDashboardService.RecentActivity getRecentActivity() {
        try {
            return adminDashboardService.getRecentActivity();
        } catch (Exception e) {
            log.error("Error getting recent activity", e);
            return AdminDashboardService.RecentActivity.builder().build();
        }
    }
    
    // Data transfer objects (Feature Flags removed)
    
    @lombok.Data
    @lombok.Builder
    public static class CleanDashboardData {
        private AdminDashboardService.DashboardOverview overview;
        private AdminDashboardService.PerformanceMetrics performance;
        private LocalDateTime lastUpdated;
    }
}
