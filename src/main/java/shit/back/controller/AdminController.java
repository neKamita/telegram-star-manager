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
import shit.back.model.FeatureFlag;
import shit.back.service.*;
import shit.back.utils.FallbackUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Enhanced admin controller with PostgreSQL integration
 */
@Slf4j
@Controller
@RequestMapping("/admin-legacy")
public class AdminController {
    
    @Autowired
    private FeatureFlagService featureFlagService;
    
    @Autowired
    private ConfigurationRefreshService configurationRefreshService;
    
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
     * Main admin dashboard with comprehensive analytics
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
            
            // Feature flags data (legacy)
            FallbackUtils.AdminData flagData = new FallbackUtils.AdminData(featureFlagService);
            
            model.addAttribute("title", "Enhanced Dashboard");
            model.addAttribute("subtitle", "PostgreSQL-powered Analytics");
            model.addAttribute("overview", overview);
            model.addAttribute("performance", performance);
            model.addAttribute("recentActivity", recentActivity);
            model.addAttribute("combinedActivity", combinedActivity);
            model.addAttribute("systemHealth", systemHealth);
            
            // Legacy feature flags data
            model.addAttribute("flags", flagData.allFlags);
            model.addAttribute("totalFlags", flagData.totalFlags);
            model.addAttribute("activeFlagsCount", flagData.activeFlagsCount);
            model.addAttribute("cacheSize", flagData.cacheSize);
            
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
     * System monitoring page
     */
    @GetMapping("/monitoring")
    public String monitoringPage(Model model) {
        try {
            AdminDashboardService.SystemHealth systemHealth = adminDashboardService.getSystemHealth();
            FallbackUtils.AdminData flagData = new FallbackUtils.AdminData(featureFlagService);
            
            model.addAttribute("title", "System Monitoring");
            model.addAttribute("subtitle", "System Health & Performance Monitoring");
            model.addAttribute("systemHealth", systemHealth);
            model.addAttribute("totalFlags", flagData.totalFlags);
            model.addAttribute("activeFlags", flagData.activeFlagsCount);
            model.addAttribute("cacheSize", flagData.cacheSize);
            
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
    
    // Legacy feature flags management
    
    @GetMapping("/feature-flags")
    public String featureFlagsPage(Model model) {
        try {
            List<FeatureFlag> flags = FallbackUtils.getAllFlagsWithFallback(featureFlagService);
            
            model.addAttribute("title", "Feature Flags");
            model.addAttribute("subtitle", "Manage all feature flags");
            model.addAttribute("flags", flags);
            return "admin/feature-flags";
        } catch (Exception e) {
            log.error("Error loading feature flags page", e);
            model.addAttribute("error", "Ошибка загрузки флагов функций: " + e.getMessage());
            return "admin/error";
        }
    }
    
    @GetMapping("/feature-flags/new")
    public String newFeatureFlagPage(Model model) {
        model.addAttribute("flag", new FeatureFlag());
        model.addAttribute("isEdit", false);
        return "admin/feature-flag-form";
    }
    
    @GetMapping("/feature-flags/{flagName}/edit")
    public String editFeatureFlagPage(@PathVariable String flagName, Model model) {
        try {
            FeatureFlag flag = featureFlagService.getFeatureFlag(flagName)
                    .orElseThrow(() -> new RuntimeException("Feature flag not found"));
            model.addAttribute("flag", flag);
            model.addAttribute("isEdit", true);
            return "admin/feature-flag-form";
        } catch (Exception e) {
            log.error("Error loading feature flag '{}' for editing", flagName, e);
            model.addAttribute("error", "Флаг функции не найден");
            return "admin/error";
        }
    }
    
    @PostMapping("/feature-flags")
    public String saveFeatureFlag(@ModelAttribute FeatureFlag flag, RedirectAttributes redirectAttributes) {
        try {
            if (flag.getName() == null || flag.getName().trim().isEmpty()) {
                throw new RuntimeException("Flag name is required");
            }
            
            // Set defaults
            if (flag.getCreatedBy() == null) {
                flag.setCreatedBy("Admin");
            }
            if (flag.getEnvironment() == null) {
                flag.setEnvironment("production");
            }
            if (flag.getVersion() == null) {
                flag.setVersion("1.0");
            }
            
            featureFlagService.createFeatureFlag(flag);
            log.info("Feature flag '{}' created via admin panel", flag.getName());
            
            redirectAttributes.addFlashAttribute("success", "Feature flag created successfully");
            return "redirect:/admin/feature-flags";
        } catch (Exception e) {
            log.error("Error creating feature flag", e);
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/feature-flags";
        }
    }
    
    @PostMapping("/feature-flags/{flagName}")
    public String updateFeatureFlag(@PathVariable String flagName, @ModelAttribute FeatureFlag flag, RedirectAttributes redirectAttributes) {
        try {
            if (flag.getName() == null || flag.getName().trim().isEmpty()) {
                flag.setName(flagName);
            }
            
            // Set defaults for update
            if (flag.getUpdatedBy() == null) {
                flag.setUpdatedBy("Admin");
            }
            
            featureFlagService.updateFeatureFlag(flagName, flag);
            log.info("Feature flag '{}' updated via admin panel", flagName);
            
            redirectAttributes.addFlashAttribute("success", "Feature flag updated successfully");
            return "redirect:/admin/feature-flags";
        } catch (Exception e) {
            log.error("Error updating feature flag '{}'", flagName, e);
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/feature-flags";
        }
    }
    
    // AJAX endpoints
    
    @PostMapping("/feature-flags/{flagName}/toggle")
    @ResponseBody
    public String toggleFeatureFlag(@PathVariable String flagName) {
        try {
            log.info("CSRF-protected toggle request for feature flag: {}", flagName);
            featureFlagService.toggleFeatureFlag(flagName, "Admin");
            configurationRefreshService.applyFeatureFlagChange(flagName);
            
            boolean isEnabled = featureFlagService.getFeatureFlag(flagName)
                    .map(FeatureFlag::isEnabled)
                    .orElse(false);
            
            log.info("Feature flag '{}' toggled to: {}", flagName, isEnabled ? "enabled" : "disabled");
            return isEnabled ? "enabled" : "disabled";
        } catch (Exception e) {
            log.error("Error toggling feature flag '{}'", flagName, e);
            return "error";
        }
    }
    
    @PostMapping("/feature-flags/{flagName}/delete")
    public String deleteFeatureFlag(@PathVariable String flagName, RedirectAttributes redirectAttributes) {
        try {
            featureFlagService.deleteFeatureFlag(flagName);
            log.info("Feature flag '{}' deleted via admin panel", flagName);
            redirectAttributes.addFlashAttribute("success", "Feature flag deleted successfully");
            return "redirect:/admin/feature-flags";
        } catch (Exception e) {
            log.error("Error deleting feature flag '{}'", flagName, e);
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/feature-flags";
        }
    }
    
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
    
    @PostMapping("/refresh-cache")
    public String refreshCache(RedirectAttributes redirectAttributes) {
        try {
            configurationRefreshService.refreshConfiguration();
            log.info("Configuration cache refreshed via admin panel");
            redirectAttributes.addFlashAttribute("success", "Cache refreshed successfully");
            return "redirect:/admin";
        } catch (Exception e) {
            log.error("Error refreshing cache", e);
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin";
        }
    }
    
    @PostMapping("/api/refresh-cache")
    @ResponseBody
    public Map<String, Object> refreshCacheApi() {
        try {
            configurationRefreshService.refreshConfiguration();
            featureFlagService.refreshCache();
            log.info("Configuration cache refreshed via API");
            
            return Map.of(
                "success", true,
                "message", "Cache refreshed successfully",
                "timestamp", LocalDateTime.now(),
                "cacheSize", featureFlagService.getCacheSize()
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
    
    @GetMapping("/api/export-config")
    public void exportConfig(jakarta.servlet.http.HttpServletResponse response) {
        try {
            response.setContentType("application/json");
            response.setHeader("Content-Disposition", "attachment; filename=feature-flags-config.json");
            
            List<FeatureFlag> flags = featureFlagService.getAllFeatureFlags();
            
            // Simple JSON export
            StringBuilder json = new StringBuilder();
            json.append("{\n  \"featureFlags\": [\n");
            
            for (int i = 0; i < flags.size(); i++) {
                FeatureFlag flag = flags.get(i);
                json.append("    {\n");
                json.append("      \"name\": \"").append(flag.getName()).append("\",\n");
                json.append("      \"description\": \"").append(flag.getDescription() != null ? flag.getDescription() : "").append("\",\n");
                json.append("      \"enabled\": ").append(flag.isEnabled()).append(",\n");
                json.append("      \"environment\": \"").append(flag.getEnvironment() != null ? flag.getEnvironment() : "").append("\",\n");
                json.append("      \"version\": \"").append(flag.getVersion() != null ? flag.getVersion() : "").append("\"\n");
                json.append("    }");
                if (i < flags.size() - 1) {
                    json.append(",");
                }
                json.append("\n");
            }
            
            json.append("  ],\n");
            json.append("  \"exportedAt\": \"").append(LocalDateTime.now()).append("\"\n");
            json.append("}");
            
            response.getWriter().write(json.toString());
            response.getWriter().flush();
            
        } catch (Exception e) {
            log.error("Error exporting config", e);
            try {
                response.getWriter().write("{\"error\": \"Failed to export config\"}");
            } catch (Exception ex) {
                log.error("Error writing error response", ex);
            }
        }
    }
    
    // API endpoints for dashboard data
    
    @GetMapping(value = "/api/dashboard-data", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public EnhancedDashboardData getDashboardData() {
        try {
            AdminDashboardService.DashboardOverview overview = adminDashboardService.getDashboardOverview();
            AdminDashboardService.PerformanceMetrics performance = adminDashboardService.getPerformanceMetrics();
            FallbackUtils.AdminData flagData = new FallbackUtils.AdminData(featureFlagService);
            
            return EnhancedDashboardData.builder()
                    .overview(overview)
                    .performance(performance)
                    .totalFlags(flagData.totalFlags)
                    .activeFlags(flagData.activeFlagsCount)
                    .cacheSize(flagData.cacheSize)
                    .lastUpdated(LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            log.error("Error getting dashboard data", e);
            return EnhancedDashboardData.builder()
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
    
    // Data transfer objects
    
    @lombok.Data
    @lombok.Builder
    public static class EnhancedDashboardData {
        private AdminDashboardService.DashboardOverview overview;
        private AdminDashboardService.PerformanceMetrics performance;
        private int totalFlags;
        private int activeFlags;
        private int cacheSize;
        private LocalDateTime lastUpdated;
    }
}
