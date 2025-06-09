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
     * Fast-loading main admin dashboard
     * Loads minimal data first, then progressive enhancement via AJAX
     */
    @GetMapping
    public String adminDashboard(Model model) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("Loading optimized admin dashboard");
            
            // 1. Быстрая загрузка только самых важных данных
            AdminDashboardCacheService.LightweightDashboardOverview lightweightData = 
                cacheService.getLightweightDashboard();
            
            // 2. Минимальные feature flags данные
            FallbackUtils.AdminData flagData = new FallbackUtils.AdminData(featureFlagService);
            
            // 3. Добавляем в модель для быстрого рендеринга
            model.addAttribute("title", "Admin Dashboard");
            model.addAttribute("subtitle", "Optimized for Performance");
            model.addAttribute("lightweightOverview", lightweightData);
            
            // Legacy feature flags (быстро)
            model.addAttribute("totalFlags", flagData.totalFlags);
            model.addAttribute("activeFlagsCount", flagData.activeFlagsCount);
            model.addAttribute("cacheSize", flagData.cacheSize);
            
            // Простые счетчики для начального отображения
            model.addAttribute("totalUsersCount", lightweightData.getTotalUsersCount());
            model.addAttribute("activeUsersCount", lightweightData.getActiveUsersCount());
            model.addAttribute("onlineUsersCount", lightweightData.getOnlineUsersCount());
            
            // Флаг для фронтенда - нужно ли загружать полные данные
            model.addAttribute("needsProgressiveLoading", true);
            model.addAttribute("dataLoaded", lightweightData.isDataLoaded());
            
            long loadTime = System.currentTimeMillis() - startTime;
            log.info("Optimized admin dashboard loaded in {}ms", loadTime);
            
            // Асинхронно прогреваем кэш для следующих запросов
            cacheService.warmupCache();
            
            return "admin/dashboard";
            
        } catch (Exception e) {
            log.error("Error loading optimized admin dashboard", e);
            
            // Fallback к минимальным данным
            model.addAttribute("title", "Admin Dashboard");
            model.addAttribute("error", "Partial data loading issue: " + e.getMessage());
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
