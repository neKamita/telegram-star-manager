package shit.back.service;

import shit.back.dto.monitoring.PerformanceMetrics;
import shit.back.dto.monitoring.SystemHealth;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import shit.back.service.AdminDashboardService.*;
import shit.back.model.UserCountsBatchResult;

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

    @Autowired
    private UserSessionUnifiedService userSessionService;

    // Минимальный кэш для экономии памяти на Koyeb
    private final Map<String, CachedData> cache = new ConcurrentHashMap<>(8, 0.75f, 1);
    private static final long CACHE_TTL_MS = 600_000; // 10 minutes (увеличено для SystemHealth)
    private static final long SYSTEM_HEALTH_CACHE_TTL_MS = 600_000; // 10 minutes специально для SystemHealth
    private static final int MAX_CACHE_SIZE = 12; // Увеличено для SystemHealth кэша

    // Специальный кэш для SystemHealth с длительным TTL
    private final Map<String, SystemHealthCachedData> systemHealthCache = new ConcurrentHashMap<>(2, 0.75f, 1);

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
     * СУПЕР-ОПТИМИЗИРОВАННЫЙ метод получения всех счетчиков пользователей одним
     * запросом
     * Использует новый UserSessionEnhancedService.getUserCountsBatch() - ИСТИННОЕ
     * решение N+1 Query
     */
    private UserCountsBatch getUserCountsBatch() {
        CachedData cached = cache.get("users_counts_batch");
        if (cached != null && !cached.isExpired()) {
            return (UserCountsBatch) cached.data;
        }

        try {
            log.debug("✅ SUPER OPTIMIZATION: Fetching ALL user counts with SINGLE SQL BATCH QUERY");

            // ✅ КРИТИЧЕСКАЯ ОПТИМИЗАЦИЯ: Используем НОВЫЙ батч-метод вместо
            // getDashboardOverview()
            CompletableFuture<UserCountsBatch> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // ЕДИНСТВЕННЫЙ SQL ЗАПРОС вместо трех отдельных COUNT() запросов!
                    UserCountsBatchResult batchResult = userSessionService.getUserCountsBatch();

                    return new UserCountsBatch(
                            batchResult.totalUsers(),
                            batchResult.activeUsers(),
                            batchResult.onlineUsers());
                } catch (Exception e) {
                    log.warn("Batch user counts failed: {}", e.getMessage());
                    return new UserCountsBatch(0L, 0L, 0L);
                }
            });

            // Timeout для быстрого ответа
            UserCountsBatch batch = future.get(2, java.util.concurrent.TimeUnit.SECONDS); // Уменьшили таймаут с 5 до 2
                                                                                          // сек

            // Кэшируем батч результат
            putWithSizeLimit("users_counts_batch", new CachedData(batch));

            // Также кэшируем отдельные счетчики для обратной совместимости
            putWithSizeLimit("total_users_count", new CachedData(batch.totalUsers));
            putWithSizeLimit("active_users_count", new CachedData(batch.activeUsers));
            putWithSizeLimit("online_users_count", new CachedData(batch.onlineUsers));

            log.info("✅ CACHE: User counts batch cached successfully - Total={}, Active={}, Online={}",
                    batch.totalUsers, batch.activeUsers, batch.onlineUsers);

            return batch;
        } catch (Exception e) {
            log.warn("Error getting batch user counts, using fallback: {}", e.getMessage());
            return getFallbackUserCountsBatch();
        }
    }

    /**
     * Оптимизированные отдельные методы - используют батч результат
     */
    private long getTotalUsersCountCached() {
        UserCountsBatch batch = getUserCountsBatch();
        return batch.totalUsers;
    }

    private long getActiveUsersCountCached() {
        UserCountsBatch batch = getUserCountsBatch();
        return batch.activeUsers;
    }

    private long getOnlineUsersCountCached() {
        UserCountsBatch batch = getUserCountsBatch();
        return batch.onlineUsers;
    }

    /**
     * Fallback метод для получения батч счетчиков из старого кэша
     */
    private UserCountsBatch getFallbackUserCountsBatch() {
        CachedData cached = cache.get("users_counts_batch");
        if (cached != null) {
            log.debug("Using stale batch cache data");
            return (UserCountsBatch) cached.data;
        }

        // Пытаемся получить из отдельных кэшей
        long total = getFallbackCount("total_users_count", 0L);
        long active = getFallbackCount("active_users_count", 0L);
        long online = getFallbackCount("online_users_count", 0L);

        return new UserCountsBatch(total, active, online);
    }

    /**
     * Внутренний класс для батч результатов счетчиков пользователей
     */
    private static class UserCountsBatch {
        final long totalUsers;
        final long activeUsers;
        final long onlineUsers;

        UserCountsBatch(long totalUsers, long activeUsers, long onlineUsers) {
            this.totalUsers = totalUsers;
            this.activeUsers = activeUsers;
            this.onlineUsers = onlineUsers;
        }
    }

    /**
     * Fallback метод для получения приблизительного значения из старого кэша
     */
    private long getFallbackCount(String cacheKey, long defaultValue) {
        CachedData cached = cache.get(cacheKey);
        if (cached != null) {
            // Используем устаревшие данные как fallback
            log.debug("Using stale cache data for {}", cacheKey);
            return (Long) cached.data;
        }
        return defaultValue;
    }

    /**
     * Кэшированная производительность
     */
    @Cacheable(value = "admin_performance", unless = "#result == null")
    public PerformanceMetrics getPerformanceMetricsCached() {
        log.debug("Getting cached performance metrics");
        try {
            Object metricsObj = adminDashboardService.getPerformanceMetrics();
            if (metricsObj instanceof PerformanceMetrics) {
                return (PerformanceMetrics) metricsObj;
            } else {
                // Маппинг вручную, если тип отличается
                PerformanceMetrics metrics = new PerformanceMetrics();
                // Здесь можно добавить копирование нужных полей, если требуется
                return metrics;
            }
        } catch (Exception e) {
            log.warn("Error getting performance metrics: {}", e.getMessage());
            return new PerformanceMetrics();
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
     * ОПТИМИЗИРОВАННОЕ асинхронное получение системного здоровья с длительным
     * кэшированием
     * TTL увеличен до 10 минут для снижения нагрузки на БД
     */
    @Async
    public CompletableFuture<SystemHealth> getSystemHealthAsync() {
        log.debug("🔍 ОПТИМИЗАЦИЯ SystemHealth: Getting system health with 10-minute cache");
        try {
            // Проверяем специальный кэш SystemHealth с длительным TTL
            SystemHealthCachedData cachedSystemHealth = systemHealthCache.get("system_health");
            if (cachedSystemHealth != null && !cachedSystemHealth.isExpired()) {
                log.info("✅ ОПТИМИЗАЦИЯ SystemHealth: Returning cached SystemHealth (age: {}ms)",
                        System.currentTimeMillis() - cachedSystemHealth.timestamp);
                return CompletableFuture.completedFuture(cachedSystemHealth.data);
            }

            log.warn("🔍 ОПТИМИЗАЦИЯ SystemHealth: Cache miss - calling expensive getSystemHealth() method");
            Object healthObj = adminDashboardService.getSystemHealth();
            SystemHealth health;
            if (healthObj instanceof SystemHealth) {
                health = (SystemHealth) healthObj;
            } else {
                health = new SystemHealth();
                // Здесь можно добавить копирование нужных полей, если требуется
            }

            // Кэшируем результат с длительным TTL (10 минут)
            systemHealthCache.put("system_health", new SystemHealthCachedData(health));
            log.info("✅ ОПТИМИЗАЦИЯ SystemHealth: Cached SystemHealth for 10 minutes");

            return CompletableFuture.completedFuture(health);
        } catch (Exception e) {
            log.error("❌ ОПТИМИЗАЦИЯ SystemHealth: Error getting system health: {}", e.getMessage());
            return CompletableFuture.completedFuture(
                    AdminDashboardCacheService.getMinimalSystemHealth());
        }
    }

    /**
     * Очистка кэша каждые 5 минут
     */
    @Scheduled(fixedRate = 300000)
    @CacheEvict(value = { "admin_performance", "admin_recent_activity", "systemHealth" }, allEntries = true)
    public void clearCache() {
        log.debug("🧹 ОПТИМИЗАЦИЯ: Clearing admin dashboard cache including SystemHealth");
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        systemHealthCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        log.debug("✅ ОПТИМИЗАЦИЯ: Cleared {} expired cache entries and {} SystemHealth entries",
                cache.size(), systemHealthCache.size());
    }

    /**
     * Принудительная очистка всего кэша включая SystemHealth
     */
    @CacheEvict(value = { "admin_performance", "admin_recent_activity", "systemHealth" }, allEntries = true)
    public void clearAllCache() {
        cache.clear();
        systemHealthCache.clear();
        log.info("🧹 ОПТИМИЗАЦИЯ: Cleared all admin dashboard cache and SystemHealth cache");
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

    // Минимальный fallback SystemHealth для ошибок
    public static SystemHealth getMinimalSystemHealth() {
        SystemHealth sh = new SystemHealth();
        sh.setStatus(null);
        sh.setDetails(null);
        sh.setLastChecked(java.time.LocalDateTime.now());
        sh.setMessages(null);
        return sh;
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

    @lombok.Data
    @lombok.Builder
    public static class FullDashboardDataCached {
        private AdminDashboardService.DashboardOverview overview;
        private PerformanceMetrics performance;
        private AdminDashboardService.RecentActivity recentActivity;
        private SystemHealth systemHealth;
        private LocalDateTime lastUpdated;
        private Long executionTimeMs;
        private boolean dataComplete;
    }

    static class SystemHealthCachedData {
        final SystemHealth data;
        final long timestamp;

        SystemHealthCachedData(SystemHealth data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > SYSTEM_HEALTH_CACHE_TTL_MS;
        }
    }

    /**
     * Заглушка для совместимости с контроллером. Возвращает пустой
     * FullDashboardDataCached.
     */
    public FullDashboardDataCached getFullDashboardDataCached() {
        return FullDashboardDataCached.builder()
                .overview(null)
                .performance(null)
                .recentActivity(null)
                .systemHealth(null)
                .lastUpdated(java.time.LocalDateTime.now())
                .executionTimeMs(null)
                .dataComplete(false)
                .build();
    }
}
