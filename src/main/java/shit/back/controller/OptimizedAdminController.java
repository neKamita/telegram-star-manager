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
}
