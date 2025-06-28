package shit.back.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * AOP аспект для отслеживания cache операций и метрик
 * Интегрируется с @Cacheable, @CachePut, @CacheEvict аннотациями
 * 
 * Принципы SOLID:
 * - Single Responsibility: только перехват cache операций
 * - Open/Closed: легко расширяется для новых типов операций
 * - Dependency Inversion: зависит от абстракций
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class CacheEventListener {

    private final CacheMetricsInterceptor cacheMetricsInterceptor;
    private final CacheManager cacheManager;

    /**
     * Перехватывает методы с @Cacheable аннотацией
     * ИСПРАВЛЕНО: Теперь правильно интегрируется со Spring Cache
     */
    @Around("@annotation(org.springframework.cache.annotation.Cacheable)")
    public Object aroundCacheable(ProceedingJoinPoint joinPoint) throws Throwable {
        String cacheName = extractCacheNameFromCacheable(joinPoint);
        Object key = generateCacheKey(joinPoint);
        String methodName = joinPoint.getSignature().getName();

        try {
            // Проверяем наличие в кэше ПЕРЕД выполнением метода
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                Cache.ValueWrapper valueWrapper = cache.get(key);
                if (valueWrapper != null) {
                    // Cache Hit
                    Object cachedResult = valueWrapper.get();
                    cacheMetricsInterceptor.onCacheLookup(cache, key, cachedResult);
                    log.info("✅ CACHE HIT: Метод {} использует кэш {}", methodName, cacheName);
                    return cachedResult;
                }
            }

            // Cache Miss - выполняем метод и кэшируем результат
            log.info("❌ CACHE MISS: Выполняем метод {} и кэшируем в {}", methodName, cacheName);
            Object result = joinPoint.proceed();

            // Записываем в кэш после выполнения
            if (cache != null && result != null) {
                cache.put(key, result);
                log.info("💾 CACHE PUT: Результат метода {} сохранен в кэш {}", methodName, cacheName);
            }

            // Регистрируем miss
            if (cache != null) {
                cacheMetricsInterceptor.onCacheLookup(cache, key, null); // Miss
            }

            return result;

        } catch (Exception e) {
            log.warn("⚠️ CACHE ERROR: Ошибка в кэш AOP для метода {}: {}", methodName, e.getMessage());
            return joinPoint.proceed();
        }
    }

    /**
     * Перехватывает методы с @CachePut аннотацией
     */
    @Around("@annotation(org.springframework.cache.annotation.CachePut)")
    public Object aroundCachePut(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();

        try {
            String cacheName = extractCacheNameFromCachePut(joinPoint);
            Object key = generateCacheKey(joinPoint);

            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cacheMetricsInterceptor.onCachePut(cache, key, result);
                log.trace("💾 Cache PUT для метода: {}", joinPoint.getSignature().getName());
            }

        } catch (Exception e) {
            log.debug("Error in cache PUT AOP: {}", e.getMessage());
        }

        return result;
    }

    /**
     * Перехватывает методы с @CacheEvict аннотацией
     */
    @Around("@annotation(org.springframework.cache.annotation.CacheEvict)")
    public Object aroundCacheEvict(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            String cacheName = extractCacheNameFromCacheEvict(joinPoint);
            Object key = generateCacheKey(joinPoint);

            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cacheMetricsInterceptor.onCacheEvict(cache, key);
                log.trace("🗑️ Cache EVICT для метода: {}", joinPoint.getSignature().getName());
            }

        } catch (Exception e) {
            log.debug("Error in cache EVICT AOP: {}", e.getMessage());
        }

        return joinPoint.proceed();
    }

    /**
     * Извлечение имени кэша из @Cacheable аннотации
     */
    private String extractCacheNameFromCacheable(ProceedingJoinPoint joinPoint) {
        try {
            org.springframework.cache.annotation.Cacheable cacheable = joinPoint.getTarget().getClass().getMethod(
                    joinPoint.getSignature().getName(),
                    ((org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature()).getParameterTypes())
                    .getAnnotation(org.springframework.cache.annotation.Cacheable.class);

            if (cacheable != null && cacheable.value().length > 0) {
                return cacheable.value()[0];
            }
        } catch (Exception e) {
            log.debug("Could not extract cache name from @Cacheable: {}", e.getMessage());
        }
        return "unknown";
    }

    /**
     * Извлечение имени кэша из @CachePut аннотации
     */
    private String extractCacheNameFromCachePut(ProceedingJoinPoint joinPoint) {
        try {
            org.springframework.cache.annotation.CachePut cachePut = joinPoint.getTarget().getClass().getMethod(
                    joinPoint.getSignature().getName(),
                    ((org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature()).getParameterTypes())
                    .getAnnotation(org.springframework.cache.annotation.CachePut.class);

            if (cachePut != null && cachePut.value().length > 0) {
                return cachePut.value()[0];
            }
        } catch (Exception e) {
            log.debug("Could not extract cache name from @CachePut: {}", e.getMessage());
        }
        return "unknown";
    }

    /**
     * Извлечение имени кэша из @CacheEvict аннотации
     */
    private String extractCacheNameFromCacheEvict(ProceedingJoinPoint joinPoint) {
        try {
            org.springframework.cache.annotation.CacheEvict cacheEvict = joinPoint.getTarget().getClass().getMethod(
                    joinPoint.getSignature().getName(),
                    ((org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature()).getParameterTypes())
                    .getAnnotation(org.springframework.cache.annotation.CacheEvict.class);

            if (cacheEvict != null && cacheEvict.value().length > 0) {
                return cacheEvict.value()[0];
            }
        } catch (Exception e) {
            log.debug("Could not extract cache name from @CacheEvict: {}", e.getMessage());
        }
        return "unknown";
    }

    /**
     * Генерация cache ключа на основе параметров метода
     */
    private Object generateCacheKey(ProceedingJoinPoint joinPoint) {
        try {
            Object[] args = joinPoint.getArgs();
            if (args.length == 0) {
                return joinPoint.getSignature().getName();
            } else if (args.length == 1) {
                return args[0];
            } else {
                return java.util.Arrays.toString(args);
            }
        } catch (Exception e) {
            log.debug("Could not generate cache key: {}", e.getMessage());
            return "unknown-key";
        }
    }
}