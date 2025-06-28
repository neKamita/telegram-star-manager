package shit.back.util;

import lombok.extern.slf4j.Slf4j;

/**
 * Утилитный класс для валидации метрик кэша
 * Обеспечивает математическую корректность Cache Hit Ratio + Cache Miss Ratio =
 * 100%
 * 
 * Принципы:
 * - Single Responsibility: только валидация метрик кэша
 * - Fail-Fast: немедленное выявление ошибок
 * - DRY: единая точка истины для валидации
 */
@Slf4j
public final class CacheMetricsValidator {

    private CacheMetricsValidator() {
        // Utility class - приватный конструктор
    }

    /**
     * Валидирует корректность метрик кэша
     * 
     * @param cacheHitRatio  процент попаданий в кэш (0-100)
     * @param cacheMissRatio процент промахов кэша (0-100)
     * @return true если метрики корректны
     * @throws IllegalArgumentException при некорректных метриках (Fail-Fast)
     */
    public static boolean validateCacheMetrics(int cacheHitRatio, int cacheMissRatio) {
        return validateCacheMetrics(cacheHitRatio, cacheMissRatio, true);
    }

    /**
     * Валидирует корректность метрик кэша с возможностью отключения исключений
     * 
     * @param cacheHitRatio  процент попаданий в кэш (0-100)
     * @param cacheMissRatio процент промахов кэша (0-100)
     * @param throwOnError   выбрасывать исключения при ошибках (Fail-Fast)
     * @return true если метрики корректны
     */
    public static boolean validateCacheMetrics(int cacheHitRatio, int cacheMissRatio, boolean throwOnError) {
        // Проверка диапазонов
        if (cacheHitRatio < 0 || cacheHitRatio > 100) {
            String error = String.format("Cache Hit Ratio должен быть в диапазоне 0-100%%, получен: %d%%",
                    cacheHitRatio);
            log.error("❌ ВАЛИДАЦИЯ КЭША: {}", error);
            if (throwOnError) {
                throw new IllegalArgumentException(error);
            }
            return false;
        }

        if (cacheMissRatio < 0 || cacheMissRatio > 100) {
            String error = String.format("Cache Miss Ratio должен быть в диапазоне 0-100%%, получен: %d%%",
                    cacheMissRatio);
            log.error("❌ ВАЛИДАЦИЯ КЭША: {}", error);
            if (throwOnError) {
                throw new IllegalArgumentException(error);
            }
            return false;
        }

        // Основная проверка: Hit + Miss = 100%
        int sum = cacheHitRatio + cacheMissRatio;
        if (sum != 100) {
            String error = String.format(
                    "Математическая ошибка: Cache Hit Ratio (%d%%) + Cache Miss Ratio (%d%%) = %d%% ≠ 100%%",
                    cacheHitRatio, cacheMissRatio, sum);
            log.error("❌ КРИТИЧЕСКАЯ ОШИБКА ВАЛИДАЦИИ: {}", error);
            if (throwOnError) {
                throw new IllegalArgumentException(error);
            }
            return false;
        }

        log.debug("✅ ВАЛИДАЦИЯ КЭША: Метрики корректны - Hit={}%, Miss={}%", cacheHitRatio, cacheMissRatio);
        return true;
    }

    /**
     * Безопасно вычисляет Cache Miss Ratio из Cache Hit Ratio
     * 
     * @param cacheHitRatio процент попаданий в кэш (0-100)
     * @return математически корректный Cache Miss Ratio
     * @throws IllegalArgumentException если cacheHitRatio некорректен
     */
    public static int calculateCacheMissRatio(int cacheHitRatio) {
        if (cacheHitRatio < 0 || cacheHitRatio > 100) {
            String error = String.format("Cache Hit Ratio должен быть в диапазоне 0-100%%, получен: %d%%",
                    cacheHitRatio);
            log.error("❌ РАСЧЕТ MISS RATIO: {}", error);
            throw new IllegalArgumentException(error);
        }

        int cacheMissRatio = 100 - cacheHitRatio;
        log.debug("✅ РАСЧЕТ MISS RATIO: Hit={}% → Miss={}%", cacheHitRatio, cacheMissRatio);
        return cacheMissRatio;
    }

    /**
     * Безопасно вычисляет Cache Hit Ratio из Cache Miss Ratio
     * 
     * @param cacheMissRatio процент промахов кэша (0-100)
     * @return математически корректный Cache Hit Ratio
     * @throws IllegalArgumentException если cacheMissRatio некорректен
     */
    public static int calculateCacheHitRatio(int cacheMissRatio) {
        if (cacheMissRatio < 0 || cacheMissRatio > 100) {
            String error = String.format("Cache Miss Ratio должен быть в диапазоне 0-100%%, получен: %d%%",
                    cacheMissRatio);
            log.error("❌ РАСЧЕТ HIT RATIO: {}", error);
            throw new IllegalArgumentException(error);
        }

        int cacheHitRatio = 100 - cacheMissRatio;
        log.debug("✅ РАСЧЕТ HIT RATIO: Miss={}% → Hit={}%", cacheMissRatio, cacheHitRatio);
        return cacheHitRatio;
    }

    /**
     * Создает корректную пару метрик кэша
     * 
     * @param cacheHitRatio желаемый Cache Hit Ratio (0-100)
     * @return массив [cacheHitRatio, cacheMissRatio]
     */
    public static int[] createValidCacheMetrics(int cacheHitRatio) {
        int cacheMissRatio = calculateCacheMissRatio(cacheHitRatio);
        return new int[] { cacheHitRatio, cacheMissRatio };
    }
}