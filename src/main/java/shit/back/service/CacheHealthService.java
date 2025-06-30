package shit.back.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import shit.back.service.metrics.CacheMetricsService;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис мониторинга здоровья кэша
 * Проверяет состояние кэша при старте и периодически
 *
 * ИСПРАВЛЕНО: Добавлен @Qualifier для устранения конфликта CacheManager'ов
 *
 * Принципы SOLID:
 * - Single Responsibility: только мониторинг кэша
 * - Open/Closed: легко расширить новыми проверками
 * - Dependency Inversion: зависимость от абстракций
 */
@Slf4j
@Service
public class CacheHealthService {

    private final CacheManager cacheManager;
    private final CacheMetricsService cacheMetricsService;
    private final RedisConnectionFactory redisConnectionFactory;

    /**
     * ИСПРАВЛЕНО: Конструктор с @Qualifier для указания основного CacheManager
     */
    public CacheHealthService(
            @Qualifier("cacheManager") CacheManager cacheManager,
            CacheMetricsService cacheMetricsService,
            RedisConnectionFactory redisConnectionFactory) {
        this.cacheManager = cacheManager;
        this.cacheMetricsService = cacheMetricsService;
        this.redisConnectionFactory = redisConnectionFactory;
        log.info("✅ CACHE HEALTH SERVICE: Инициализирован с основным CacheManager: {}",
                cacheManager.getClass().getSimpleName());
    }

    private final Map<String, LocalDateTime> lastCacheActivity = new ConcurrentHashMap<>();
    private volatile boolean cacheSystemHealthy = false;

    /**
     * Диагностика кэша при старте приложения
     */
    @EventListener(ApplicationReadyEvent.class)
    public void performStartupCacheDiagnostic() {
        log.info("🔍 STARTUP CACHE DIAGNOSTIC: ===== НАЧАЛО ДИАГНОСТИКИ КЭША =====");

        // 1. Проверка CacheManager
        diagnoseCacheManager();

        // 2. Проверка Redis
        diagnoseRedisConnection();

        // 3. Тестирование кэширования
        testCacheOperations();

        // 4. Проверка метрик кэша
        checkCacheMetrics();

        log.info("🔍 STARTUP CACHE DIAGNOSTIC: ===== ДИАГНОСТИКА ЗАВЕРШЕНА =====");
        log.info("🏥 CACHE HEALTH: Система кэширования {}",
                cacheSystemHealthy ? "РАБОТАЕТ КОРРЕКТНО" : "ИМЕЕТ ПРОБЛЕМЫ");
    }

    /**
     * Диагностика CacheManager
     */
    private void diagnoseCacheManager() {
        log.info("🔍 ДИАГНОСТИКА CACHE MANAGER:");

        if (cacheManager == null) {
            log.error("❌ CacheManager не инициализирован!");
            return;
        }

        log.info("✅ CacheManager тип: {}", cacheManager.getClass().getSimpleName());
        log.info("✅ Доступные кэши: {}", cacheManager.getCacheNames());

        // Проверяем каждый кэш
        for (String cacheName : cacheManager.getCacheNames()) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                log.info("✅ Кэш '{}' инициализирован, тип: {}",
                        cacheName, cache.getNativeCache().getClass().getSimpleName());
            } else {
                log.warn("⚠️ Кэш '{}' не найден", cacheName);
            }
        }
    }

    /**
     * Диагностика Redis подключения
     */
    private void diagnoseRedisConnection() {
        log.info("🔍 ДИАГНОСТИКА REDIS:");

        if (redisConnectionFactory == null) {
            log.warn("⚠️ Redis ConnectionFactory не настроен - используется локальный кэш");
            return;
        }

        try {
            // Проверяем ping
            redisConnectionFactory.getConnection().ping();
            log.info("✅ Redis подключение активно");

            // Дополнительные проверки
            log.info("✅ Redis ConnectionFactory тип: {}",
                    redisConnectionFactory.getClass().getSimpleName());

        } catch (Exception e) {
            log.error("❌ Redis подключение неудачно: {}", e.getMessage());
            log.warn("🔄 Система переключилась на локальный кэш");
        }
    }

    /**
     * Тестирование операций кэширования
     */
    private void testCacheOperations() {
        log.info("🔍 ТЕСТИРОВАНИЕ КЭША:");

        try {
            // Тестируем основной кэш
            Cache testCache = cacheManager.getCache("admin_performance");
            if (testCache != null) {
                String testKey = "startup_test_" + System.currentTimeMillis();
                String testValue = "test_value_" + System.currentTimeMillis();

                // PUT операция
                testCache.put(testKey, testValue);
                log.info("✅ PUT операция успешна");

                // GET операция
                Cache.ValueWrapper wrapper = testCache.get(testKey);
                if (wrapper != null && testValue.equals(wrapper.get())) {
                    log.info("✅ GET операция успешна - кэш работает корректно");
                    cacheSystemHealthy = true;

                    // Очищаем тестовые данные
                    testCache.evict(testKey);
                    log.info("✅ EVICT операция успешна");
                } else {
                    log.error("❌ GET операция неудачна - кэш не работает");
                }
            } else {
                log.error("❌ Тестовый кэш 'admin_performance' не найден");
            }

        } catch (Exception e) {
            log.error("❌ Ошибка тестирования кэша: {}", e.getMessage());
            cacheSystemHealthy = false;
        }
    }

    /**
     * Проверка метрик кэша
     */
    private void checkCacheMetrics() {
        log.info("🔍 ПРОВЕРКА МЕТРИК КЭША:");

        try {
            if (cacheMetricsService.isAvailable()) {
                log.info("✅ CacheMetricsService доступен");

                // Получаем детальную статистику
                Map<String, Object> stats = cacheMetricsService.getDetailedCacheStatistics();
                log.info("✅ Детальная статистика кэша получена: {}", stats.size());

                // Логируем ключевые метрики
                if (stats.containsKey("totalCacheHitRatio")) {
                    log.info("📊 Cache Hit Ratio: {}%", stats.get("totalCacheHitRatio"));
                }
                if (stats.containsKey("totalCacheMissRatio")) {
                    log.info("📊 Cache Miss Ratio: {}%", stats.get("totalCacheMissRatio"));
                }
                if (stats.containsKey("cacheProvider")) {
                    log.info("📊 Cache Provider: {}", stats.get("cacheProvider"));
                }

            } else {
                log.warn("⚠️ CacheMetricsService недоступен");
            }

        } catch (Exception e) {
            log.error("❌ Ошибка проверки метрик кэша: {}", e.getMessage());
        }
    }

    /**
     * Периодическая проверка здоровья кэша (каждые 5 минут)
     */
    @Scheduled(fixedRate = 300000)
    public void periodicCacheHealthCheck() {
        log.debug("🏥 PERIODIC CACHE HEALTH CHECK: Проверка здоровья кэша...");

        try {
            // Быстрая проверка основных кэшей
            boolean allCachesHealthy = true;

            for (String cacheName : cacheManager.getCacheNames()) {
                Cache cache = cacheManager.getCache(cacheName);
                if (cache == null) {
                    log.warn("⚠️ PERIODIC CHECK: Кэш '{}' недоступен", cacheName);
                    allCachesHealthy = false;
                } else {
                    lastCacheActivity.put(cacheName, LocalDateTime.now());
                }
            }

            if (allCachesHealthy) {
                log.debug("✅ PERIODIC CHECK: Все кэши работают корректно");
            } else {
                log.warn("⚠️ PERIODIC CHECK: Обнаружены проблемы с кэшами");
            }

            // Проверка Redis если доступен
            if (redisConnectionFactory != null) {
                try {
                    redisConnectionFactory.getConnection().ping();
                    log.debug("✅ PERIODIC CHECK: Redis подключение активно");
                } catch (Exception e) {
                    log.warn("⚠️ PERIODIC CHECK: Redis недоступен: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("❌ PERIODIC CHECK: Ошибка периодической проверки кэша: {}", e.getMessage());
        }
    }

    /**
     * Получить статус здоровья кэша
     */
    public boolean isCacheSystemHealthy() {
        return cacheSystemHealthy;
    }

    /**
     * Получить информацию о последней активности кэшей
     */
    public Map<String, LocalDateTime> getLastCacheActivity() {
        return new ConcurrentHashMap<>(lastCacheActivity);
    }

    /**
     * Принудительная диагностика кэша
     */
    public void forceCacheDiagnostic() {
        log.info("🔧 FORCE DIAGNOSTIC: Принудительная диагностика кэша");
        performStartupCacheDiagnostic();
    }
}