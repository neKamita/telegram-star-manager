package shit.back.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import shit.back.service.AdminDashboardService.*;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Cached version of AdminDashboardService for performance optimization
 * Especially optimized for Koyeb's limited resources (0.1 vCPU, 512MB RAM)
 */
@Slf4j
@Service
public class AdminDashboardCacheService {

    @Autowired
    private AdminDashboardService adminDashboardService;

    // Минимальный кэш для экономии памяти на Koyeb
    private final Map<String, CachedData> cache = new ConcurrentHashMap<>(8, 0.75f, 1);
    private static final long CACHE_TTL_MS = 120_000; // 2 minutes (уменьшено)
    private static final int MAX_CACHE_SIZE = 10; // Максимум 10 записей

    /**
     * Fast lightweight dashboard overview - only essential data
     */
    public LightweightDashboardOverview getLightweightDashboard() {
        log.debug("Getting lightweight dashboard overview");
        
        try {
            // Используем кэшированные данные если доступны
            CachedData cachedOverview = cache.get("lightweight_overview");
            if (cachedOverview != null && !cachedOverview.isExpired()) {
                log.debug("Returning cached lightweight dashboard");
                return (LightweightDashboardOverview) cachedOverview.data;
            }

            // Получаем только самые важные данные
            long totalUsers = getTotalUsersCountCached();
            long activeUsers = getActiveUsersCountCached();
            long onlineUsers = getOnlineUsersCountCached();
            
            LightweightDashboardOverview overview = LightweightDashboardOverview.builder()
                    .totalUsersCount(totalUsers)
                    .activeUsersCount(activeUsers)
                    .onlineUsersCount(onlineUsers)
                    .lastUpdated(LocalDateTime.now())
                    .dataLoaded(true)
                    .build();

            // Кэшируем результат с проверкой размера
            putWithSizeLimit("lightweight_overview", new CachedData(overview));
            log.debug("Cached lightweight dashboard overview");
            
            return overview;
        } catch (Exception e) {
            log.warn("Error getting lightweight dashboard, returning minimal data: {}", e.getMessage());
            return LightweightDashboardOverview.builder()
                    .totalUsersCount(0)
                    .activeUsersCount(0)
                    .onlineUsersCount(0)
                    .lastUpdated(LocalDateTime.now())
                    .dataLoaded(false)
                    .build();
        }
    }

    /**
     * Асинхронная загрузка полных данных dashboard
     */
    @Async("adminAsyncExecutor")
    public CompletableFuture<DashboardOverview> getFullDashboardAsync() {
        log.debug("Loading full dashboard data asynchronously");
        
        try {
            CachedData cachedData = cache.get("full_dashboard");
            if (cachedData != null && !cachedData.isExpired()) {
                log.debug("Returning cached full dashboard");
                return CompletableFuture.completedFuture((DashboardOverview) cachedData.data);
            }

            DashboardOverview overview = adminDashboardService.getDashboardOverview();
            cache.put("full_dashboard", new CachedData(overview));
            
            log.debug("Full dashboard loaded and cached");
            return CompletableFuture.completedFuture(overview);
        } catch (Exception e) {
            log.error("Error loading full dashboard async: {}", e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Кэшированный счетчик пользователей
     */
    private long getTotalUsersCountCached() {
        CachedData cached = cache.get("total_users_count");
        if (cached != null && !cached.isExpired()) {
            return (Long) cached.data;
        }

        try {
            // Простой быстрый запрос
            long count = adminDashboardService.getDashboardOverview().getTotalUsersCount();
            cache.put("total_users_count", new CachedData(count));
            return count;
        } catch (Exception e) {
            log.warn("Error getting total users count: {}", e.getMessage());
            return 0;
        }
    }

    private long getActiveUsersCountCached() {
        CachedData cached = cache.get("active_users_count");
        if (cached != null && !cached.isExpired()) {
            return (Long) cached.data;
        }

        try {
            long count = adminDashboardService.getDashboardOverview().getActiveUsersCount();
            cache.put("active_users_count", new CachedData(count));
            return count;
        } catch (Exception e) {
            log.warn("Error getting active users count: {}", e.getMessage());
            return 0;
        }
    }

    private long getOnlineUsersCountCached() {
        CachedData cached = cache.get("online_users_count");
        if (cached != null && !cached.isExpired()) {
            return (Long) cached.data;
        }

        try {
            long count = adminDashboardService.getDashboardOverview().getOnlineUsersCount();
            cache.put("online_users_count", new CachedData(count));
            return count;
        } catch (Exception e) {
            log.warn("Error getting online users count: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Кэшированная производительность
     */
    @Cacheable(value = "admin_performance", unless = "#result == null")
    public PerformanceMetrics getPerformanceMetricsCached() {
        log.debug("Getting cached performance metrics");
        try {
            return adminDashboardService.getPerformanceMetrics();
        } catch (Exception e) {
            log.warn("Error getting performance metrics: {}", e.getMessage());
            return PerformanceMetrics.builder().build();
        }
    }

    /**
     * Кэшированная недавняя активность (упрощенная)
     */
    @Cacheable(value = "admin_recent_activity", unless = "#result == null")
    public SimplifiedRecentActivity getRecentActivityCached() {
        log.debug("Getting cached recent activity");
        try {
            RecentActivity fullActivity = adminDashboardService.getRecentActivity();
            
            // Упрощаем данные для быстрой загрузки
            return SimplifiedRecentActivity.builder()
                    .totalRecentOrders(fullActivity.getTotalRecentOrders())
                    .totalNewUsers(fullActivity.getTotalNewUsers())
                    .totalOnlineUsers(fullActivity.getTotalOnlineUsers())
                    .totalTodaysOrders(fullActivity.getTotalTodaysOrders())
                    .lastUpdated(LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            log.warn("Error getting recent activity: {}", e.getMessage());
            return SimplifiedRecentActivity.builder()
                    .lastUpdated(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * Асинхронное получение системного здоровья
     */
    @Async
    public CompletableFuture<SystemHealth> getSystemHealthAsync() {
        log.debug("Getting system health asynchronously");
        try {
            SystemHealth health = adminDashboardService.getSystemHealth();
            return CompletableFuture.completedFuture(health);
        } catch (Exception e) {
            log.error("Error getting system health: {}", e.getMessage());
            return CompletableFuture.completedFuture(
                SystemHealth.builder()
                    .healthScore(50)
                    .lastChecked(LocalDateTime.now())
                    .build()
            );
        }
    }

    /**
     * Очистка кэша каждые 5 минут
     */
    @Scheduled(fixedRate = 300000)
    @CacheEvict(value = {"admin_performance", "admin_recent_activity"}, allEntries = true)
    public void clearCache() {
        log.debug("Clearing admin dashboard cache");
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        log.debug("Cleared {} expired cache entries", cache.size());
    }

    /**
     * Принудительная очистка всего кэша
     */
    @CacheEvict(value = {"admin_performance", "admin_recent_activity"}, allEntries = true)
    public void clearAllCache() {
        cache.clear();
        log.info("Cleared all admin dashboard cache");
    }

    /**
     * Прогрев кэша
     */
    @Async
    public void warmupCache() {
        log.info("Warming up admin dashboard cache");
        try {
            getLightweightDashboard();
            getPerformanceMetricsCached();
            getRecentActivityCached();
            log.info("Admin dashboard cache warmed up successfully");
        } catch (Exception e) {
            log.warn("Error warming up cache: {}", e.getMessage());
        }
    }

    /**
     * Добавляет элемент в кэш с ограничением размера для экономии памяти
     */
    private void putWithSizeLimit(String key, CachedData data) {
        // Если кэш переполнен, удаляем старые записи
        if (cache.size() >= MAX_CACHE_SIZE) {
            // Удаляем самые старые записи
            cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
            
            // Если все еще переполнен, удаляем случайную запись
            if (cache.size() >= MAX_CACHE_SIZE) {
                String oldestKey = cache.keySet().iterator().next();
                cache.remove(oldestKey);
                log.debug("Removed cache entry '{}' due to size limit", oldestKey);
            }
        }
        
        cache.put(key, data);
    }

    // Внутренние классы для кэширования

    private static class CachedData {
        final Object data;
        final long timestamp;

        CachedData(Object data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    // DTO для оптимизированных данных

    @lombok.Data
    @lombok.Builder
    public static class LightweightDashboardOverview {
        private long totalUsersCount;
        private long activeUsersCount;
        private long onlineUsersCount;
        private LocalDateTime lastUpdated;
        private boolean dataLoaded;
    }

    @lombok.Data
    @lombok.Builder
    public static class SimplifiedRecentActivity {
        private int totalRecentOrders;
        private int totalNewUsers;
        private int totalOnlineUsers;
        private int totalTodaysOrders;
        private LocalDateTime lastUpdated;
    }
}
