package shit.back.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import shit.back.service.AdminDashboardCacheService;
import shit.back.service.AdminDashboardService;
import shit.back.service.FeatureFlagService;
import shit.back.utils.FallbackUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Optimized admin controller for Koyeb's limited resources
 * Focuses on fast initial page load with progressive data loading
 */
@Slf4j
@Controller
@RequestMapping("/admin")
public class OptimizedAdminController {
    
    @Autowired
    private AdminDashboardCacheService cacheService;
    
    @Autowired
    private FeatureFlagService featureFlagService;

    /**
     * Ultra-fast admin dashboard - minimal memory footprint
     * Only essential data, no heavy operations
     */
    @GetMapping
    public String adminDashboard(Model model) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("Loading ultra-lightweight admin dashboard");
            
            // Только самые критичные данные без тяжелых запросов
            model.addAttribute("title", "Admin Dashboard");
            model.addAttribute("subtitle", "Memory Optimized");
            
            // Минимальные статические данные
            model.addAttribute("totalUsersCount", "Loading...");
            model.addAttribute("activeUsersCount", "Loading...");
            model.addAttribute("onlineUsersCount", "Loading...");
            
            // Feature flags - только счетчики
            try {
                int totalFlags = featureFlagService.getAllFeatureFlags().size();
                model.addAttribute("totalFlags", totalFlags);
                model.addAttribute("activeFlagsCount", totalFlags);
                model.addAttribute("cacheSize", featureFlagService.getCacheSize());
            } catch (Exception e) {
                log.warn("Error getting feature flags: {}", e.getMessage());
                model.addAttribute("totalFlags", 0);
                model.addAttribute("activeFlagsCount", 0);
                model.addAttribute("cacheSize", 0);
            }
            
            // Полностью полагаемся на AJAX для данных
            model.addAttribute("needsProgressiveLoading", true);
            model.addAttribute("dataLoaded", false);
            model.addAttribute("ultraLightweight", true);
            
            long loadTime = System.currentTimeMillis() - startTime;
            log.info("Ultra-lightweight admin dashboard loaded in {}ms", loadTime);
            
            return "admin/dashboard";
            
        } catch (Exception e) {
            log.error("Error loading ultra-lightweight dashboard", e);
            
            // Минимальный fallback
            model.addAttribute("title", "Admin Dashboard");
            model.addAttribute("subtitle", "Error Recovery Mode");
            model.addAttribute("error", "Please refresh the page");
            model.addAttribute("totalUsersCount", 0);
            model.addAttribute("activeUsersCount", 0);
            model.addAttribute("onlineUsersCount", 0);
            model.addAttribute("needsProgressiveLoading", false);
            model.addAttribute("dataLoaded", false);
            
            return "admin/dashboard";
        }
    }

    /**
     * API endpoint для прогрессивной загрузки полных данных dashboard
     */
    @GetMapping(value = "/api/dashboard-full", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public CompletableFuture<Map<String, Object>> getFullDashboardData() {
        log.debug("Loading full dashboard data via API");
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Используем кэшированный сервис
                AdminDashboardService.DashboardOverview overview = 
                    cacheService.getFullDashboardAsync().join();
                
                AdminDashboardCacheService.SimplifiedRecentActivity recentActivity = 
                    cacheService.getRecentActivityCached();
                
                AdminDashboardService.PerformanceMetrics performance = 
                    cacheService.getPerformanceMetricsCached();
                
                return Map.of(
                    "success", true,
                    "overview", overview,
                    "recentActivity", recentActivity,
                    "performance", performance,
                    "lastUpdated", LocalDateTime.now()
                );
                
            } catch (Exception e) {
                log.error("Error loading full dashboard data", e);
                return Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "lastUpdated", LocalDateTime.now()
                );
            }
        });
    }

    /**
     * API endpoint для получения системного здоровья
     */
    @GetMapping(value = "/api/system-health", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public CompletableFuture<AdminDashboardService.SystemHealth> getSystemHealth() {
        log.debug("Getting system health via API");
        return cacheService.getSystemHealthAsync();
    }

    /**
     * API endpoint для быстрого обновления счетчиков
     */
    @GetMapping(value = "/api/quick-stats", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public AdminDashboardCacheService.LightweightDashboardOverview getQuickStats() {
        log.debug("Getting quick stats");
        return cacheService.getLightweightDashboard();
    }

    /**
     * API endpoint для управления кэшем
     */
    @PostMapping(value = "/api/cache/clear", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> clearCache() {
        try {
            log.info("Clearing admin dashboard cache via API");
            cacheService.clearAllCache();
            
            return Map.of(
                "success", true,
                "message", "Cache cleared successfully",
                "timestamp", LocalDateTime.now()
            );
        } catch (Exception e) {
            log.error("Error clearing cache", e);
            return Map.of(
                "success", false,
                "error", e.getMessage(),
                "timestamp", LocalDateTime.now()
            );
        }
    }

    /**
     * API endpoint для прогрева кэша
     */
    @PostMapping(value = "/api/cache/warmup", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public CompletableFuture<Map<String, Object>> warmupCache() {
        log.info("Cache warmup requested via API");
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                cacheService.warmupCache();
                return Map.of(
                    "success", true,
                    "message", "Cache warmed up successfully",
                    "timestamp", LocalDateTime.now()
                );
            } catch (Exception e) {
                log.error("Error warming up cache", e);
                return Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "timestamp", LocalDateTime.now()
                );
            }
        });
    }

    /**
     * Simplified feature flags endpoint
     */
    @GetMapping(value = "/api/feature-flags-summary", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> getFeatureFlagsSummary() {
        try {
            FallbackUtils.AdminData flagData = new FallbackUtils.AdminData(featureFlagService);
            
            return Map.of(
                "totalFlags", flagData.totalFlags,
                "activeFlags", flagData.activeFlagsCount,
                "cacheSize", flagData.cacheSize,
                "lastUpdated", LocalDateTime.now()
            );
        } catch (Exception e) {
            log.error("Error getting feature flags summary", e);
            return Map.of(
                "totalFlags", 0,
                "activeFlags", 0,
                "cacheSize", 0,
                "error", e.getMessage(),
                "lastUpdated", LocalDateTime.now()
            );
        }
    }

    /**
     * API endpoint для получения быстрых счетчиков пользователей
     */
    @GetMapping(value = "/api/users/count", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> getUsersCount() {
        try {
            log.debug("Getting users count via API");
            AdminDashboardCacheService.LightweightDashboardOverview overview = 
                cacheService.getLightweightDashboard();
            
            return Map.of(
                "success", true,
                "totalUsers", overview.getTotalUsersCount(),
                "activeUsers", overview.getActiveUsersCount(),
                "onlineUsers", overview.getOnlineUsersCount(),
                "dataLoaded", overview.isDataLoaded(),
                "timestamp", LocalDateTime.now()
            );
        } catch (Exception e) {
            log.error("Error getting users count", e);
            return Map.of(
                "success", false,
                "error", e.getMessage(),
                "totalUsers", 0,
                "activeUsers", 0,
                "onlineUsers", 0,
                "timestamp", LocalDateTime.now()
            );
        }
    }

    /**
     * Health check для admin панели
     */
    @GetMapping(value = "/api/health", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> adminHealthCheck() {
        try {
            AdminDashboardCacheService.LightweightDashboardOverview overview = 
                cacheService.getLightweightDashboard();
            
            return Map.of(
                "status", "UP",
                "dataLoaded", overview.isDataLoaded(),
                "totalUsers", overview.getTotalUsersCount(),
                "activeUsers", overview.getActiveUsersCount(),
                "onlineUsers", overview.getOnlineUsersCount(),
                "timestamp", LocalDateTime.now()
            );
        } catch (Exception e) {
            log.error("Admin health check failed", e);
            return Map.of(
                "status", "DOWN",
                "error", e.getMessage(),
                "timestamp", LocalDateTime.now()
            );
        }
    }

    // ==================== ADMIN PAGE ENDPOINTS ====================

    /**
     * Activity Logs page - фиксирует 404 ошибку
     */
    @GetMapping("/activity-logs")
    public String activityLogs(Model model) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("Loading activity logs page");
            
            model.addAttribute("title", "Activity Logs");
            model.addAttribute("subtitle", "User Activity Monitoring");
            model.addAttribute("pageSection", "activity-logs");
            
            // Основные данные загружаются через AJAX для оптимизации
            model.addAttribute("logsCount", "Loading...");
            model.addAttribute("progressiveLoading", true);
            
            long loadTime = System.currentTimeMillis() - startTime;
            log.info("Activity logs page loaded in {}ms", loadTime);
            
            return "admin/activity-logs";
            
        } catch (Exception e) {
            log.error("Error loading activity logs page", e);
            model.addAttribute("error", "Failed to load activity logs");
            return "admin/error";
        }
    }

    /**
     * Feature Flags page
     */
    @GetMapping("/feature-flags")
    public String featureFlags(Model model) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("Loading feature flags page");
            
            model.addAttribute("title", "Feature Flags");
            model.addAttribute("subtitle", "Application Features Management");
            model.addAttribute("pageSection", "feature-flags");
            
            // Минимальные данные для быстрой загрузки
            try {
                int totalFlags = featureFlagService.getAllFeatureFlags().size();
                model.addAttribute("totalFlags", totalFlags);
                model.addAttribute("cacheSize", featureFlagService.getCacheSize());
            } catch (Exception e) {
                log.warn("Error getting feature flags count: {}", e.getMessage());
                model.addAttribute("totalFlags", 0);
                model.addAttribute("cacheSize", 0);
            }
            
            model.addAttribute("progressiveLoading", true);
            
            long loadTime = System.currentTimeMillis() - startTime;
            log.info("Feature flags page loaded in {}ms", loadTime);
            
            return "admin/feature-flags";
            
        } catch (Exception e) {
            log.error("Error loading feature flags page", e);
            model.addAttribute("error", "Failed to load feature flags");
            return "admin/error";
        }
    }

    /**
     * Feature Flag Form page (для создания/редактирования)
     */
    @GetMapping("/feature-flags/new")
    public String newFeatureFlag(Model model) {
        try {
            log.info("Loading new feature flag form");
            
            model.addAttribute("title", "New Feature Flag");
            model.addAttribute("subtitle", "Create Feature Flag");
            model.addAttribute("pageSection", "feature-flags");
            model.addAttribute("formMode", "create");
            
            return "admin/feature-flag-form";
            
        } catch (Exception e) {
            log.error("Error loading feature flag form", e);
            model.addAttribute("error", "Failed to load form");
            return "admin/error";
        }
    }

    /**
     * Feature Flag Edit Form page
     */
    @GetMapping("/feature-flags/edit/{flagName}")
    public String editFeatureFlag(@PathVariable String flagName, Model model) {
        try {
            log.info("Loading edit feature flag form for: {}", flagName);
            
            model.addAttribute("title", "Edit Feature Flag");
            model.addAttribute("subtitle", "Edit: " + flagName);
            model.addAttribute("pageSection", "feature-flags");
            model.addAttribute("formMode", "edit");
            model.addAttribute("flagName", flagName);
            
            return "admin/feature-flag-form";
            
        } catch (Exception e) {
            log.error("Error loading feature flag edit form for {}", flagName, e);
            model.addAttribute("error", "Failed to load edit form");
            return "admin/error";
        }
    }

    /**
     * Monitoring page
     */
    @GetMapping("/monitoring")
    public String monitoring(Model model) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("Loading monitoring page");
            
            model.addAttribute("title", "System Monitoring");
            model.addAttribute("subtitle", "Performance & Health Metrics");
            model.addAttribute("pageSection", "monitoring");
            
            // Минимальные данные для быстрой загрузки
            model.addAttribute("systemStatus", "Loading...");
            model.addAttribute("memoryUsage", "Loading...");
            model.addAttribute("progressiveLoading", true);
            
            long loadTime = System.currentTimeMillis() - startTime;
            log.info("Monitoring page loaded in {}ms", loadTime);
            
            return "admin/monitoring";
            
        } catch (Exception e) {
            log.error("Error loading monitoring page", e);
            model.addAttribute("error", "Failed to load monitoring");
            return "admin/error";
        }
    }

    // ==================== API ENDPOINTS FOR PAGES ====================

    /**
     * API endpoint для загрузки activity logs данных
     */
    @GetMapping(value = "/api/activity-logs", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public CompletableFuture<Map<String, Object>> getActivityLogsData() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Loading activity logs data via API");
                
                // Здесь будет логика получения activity logs
                // Пока возвращаем заглушку для быстрого исправления 404
                return Map.of(
                    "success", true,
                    "logs", java.util.Collections.emptyList(),
                    "totalCount", 0,
                    "message", "Activity logs data loaded",
                    "timestamp", LocalDateTime.now()
                );
                
            } catch (Exception e) {
                log.error("Error loading activity logs data", e);
                return Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "logs", java.util.Collections.emptyList(),
                    "timestamp", LocalDateTime.now()
                );
            }
        });
    }

    /**
     * API endpoint для загрузки monitoring данных
     */
    @GetMapping(value = "/api/monitoring", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public CompletableFuture<Map<String, Object>> getMonitoringData() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Loading monitoring data via API");
                
                AdminDashboardService.SystemHealth systemHealth = 
                    cacheService.getSystemHealthAsync().join();
                
                AdminDashboardService.PerformanceMetrics performance = 
                    cacheService.getPerformanceMetricsCached();
                
                return Map.of(
                    "success", true,
                    "systemHealth", systemHealth,
                    "performance", performance,
                    "timestamp", LocalDateTime.now()
                );
                
            } catch (Exception e) {
                log.error("Error loading monitoring data", e);
                return Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "timestamp", LocalDateTime.now()
                );
            }
        });
    }
}
