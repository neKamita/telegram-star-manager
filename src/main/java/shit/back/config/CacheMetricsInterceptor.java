package shit.back.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheInterceptor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import shit.back.service.metrics.CacheMetricsService;

/**
 * Интерцептор для отслеживания cache hit/miss метрик в реальном времени
 * Интегрируется с Spring Cache для автоматического сбора статистики
 *
 * Следует принципам:
 * - Single Responsibility: только мониторинг cache событий
 * - Open/Closed: расширение функциональности без изменения существующего кода
 * - Dependency Inversion: зависимость от абстракции CacheMetricsService
 *
 * ИСПРАВЛЕНО: Циклическая зависимость устранена с помощью @Lazy инъекции
 */
@Slf4j
@Component
public class CacheMetricsInterceptor {

    private final CacheMetricsService cacheMetricsService;

    public CacheMetricsInterceptor(@Lazy CacheMetricsService cacheMetricsService) {
        this.cacheMetricsService = cacheMetricsService;
    }

    /**
     * Обработка cache lookup событий
     * 
     * @param cache целевой кэш
     * @param key   ключ поиска
     * @param value найденное значение (null если miss)
     */
    public void onCacheLookup(Cache cache, Object key, Object value) {
        try {
            String cacheName = cache.getName();

            if (value != null) {
                // Cache Hit
                cacheMetricsService.recordCacheHit(cacheName);
                log.trace("✅ Cache HIT для '{}' с ключом: {}", cacheName, key);
            } else {
                // Cache Miss
                cacheMetricsService.recordCacheMiss(cacheName);
                log.trace("❌ Cache MISS для '{}' с ключом: {}", cacheName, key);
            }

        } catch (Exception e) {
            log.debug("Ошибка записи cache метрики: {}", e.getMessage());
        }
    }

    /**
     * Обработка cache put событий
     * 
     * @param cache целевой кэш
     * @param key   ключ записи
     * @param value записываемое значение
     */
    public void onCachePut(Cache cache, Object key, Object value) {
        try {
            String cacheName = cache.getName();
            log.trace("💾 Cache PUT для '{}' с ключом: {}", cacheName, key);

            // Можно добавить дополнительную статистику для PUT операций
            // if needed for future extensions

        } catch (Exception e) {
            log.debug("Ошибка записи cache put метрики: {}", e.getMessage());
        }
    }

    /**
     * Обработка cache eviction событий
     * 
     * @param cache целевой кэш
     * @param key   ключ удаления
     */
    public void onCacheEvict(Cache cache, Object key) {
        try {
            String cacheName = cache.getName();
            log.trace("🗑️ Cache EVICT для '{}' с ключом: {}", cacheName, key);

            // Можно добавить дополнительную статистику для EVICT операций
            // if needed for future extensions

        } catch (Exception e) {
            log.debug("Ошибка записи cache evict метрики: {}", e.getMessage());
        }
    }

    /**
     * Обработка cache clear событий
     * 
     * @param cache целевой кэш
     */
    public void onCacheClear(Cache cache) {
        try {
            String cacheName = cache.getName();
            log.trace("🧹 Cache CLEAR для '{}'", cacheName);

            // Можно добавить дополнительную статистику для CLEAR операций
            // if needed for future extensions

        } catch (Exception e) {
            log.debug("Ошибка записи cache clear метрики: {}", e.getMessage());
        }
    }
}