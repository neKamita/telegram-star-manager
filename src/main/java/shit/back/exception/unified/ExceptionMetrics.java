package shit.back.exception.unified;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Компонент для сбора метрик исключений
 * Обеспечивает мониторинг и статистику обработки ошибок
 * 
 * @author TelegramStarManager - Stage 3 Refactoring
 * @since Exception Unification Architecture
 */
@Component
public class ExceptionMetrics {

    private static final Logger log = LoggerFactory.getLogger(ExceptionMetrics.class);

    // Счетчики исключений по типам
    private final Map<String, AtomicLong> exceptionCounts = new ConcurrentHashMap<>();

    // Счетчики исключений по путям
    private final Map<String, AtomicLong> pathExceptionCounts = new ConcurrentHashMap<>();

    // Общие метрики
    private final AtomicLong totalExceptions = new AtomicLong(0);
    private final AtomicLong criticalExceptions = new AtomicLong(0);
    private final AtomicLong validationExceptions = new AtomicLong(0);
    private final AtomicLong securityExceptions = new AtomicLong(0);
    private final AtomicLong businessExceptions = new AtomicLong(0);

    // Временные метрики
    private volatile LocalDateTime lastExceptionTime = LocalDateTime.now();
    private volatile LocalDateTime lastCriticalExceptionTime;

    /**
     * Регистрация исключения
     */
    public void recordException(String errorCode, WebRequest request) {
        try {
            // Увеличиваем общий счетчик
            totalExceptions.incrementAndGet();

            // Увеличиваем счетчик по типу ошибки
            exceptionCounts.computeIfAbsent(errorCode, k -> new AtomicLong(0))
                    .incrementAndGet();

            // Увеличиваем счетчик по пути
            String path = extractPath(request);
            pathExceptionCounts.computeIfAbsent(path, k -> new AtomicLong(0))
                    .incrementAndGet();

            // Обновляем время последнего исключения
            lastExceptionTime = LocalDateTime.now();

            // Категоризируем исключение
            categorizeException(errorCode);

            // Логируем каждое 100-е исключение для мониторинга
            if (totalExceptions.get() % 100 == 0) {
                log.info("📊 Обработано {} исключений. Последнее: {} на пути {}",
                        totalExceptions.get(), errorCode, path);
            }

        } catch (Exception e) {
            // Не должны падать при сборе метрик
            log.error("Ошибка при регистрации метрик исключения: {}", e.getMessage());
        }
    }

    /**
     * Регистрация критического исключения
     */
    public void recordCriticalError() {
        try {
            criticalExceptions.incrementAndGet();
            lastCriticalExceptionTime = LocalDateTime.now();

            log.warn("🚨 Зарегистрировано критическое исключение. Всего критических: {}",
                    criticalExceptions.get());
        } catch (Exception e) {
            log.error("Ошибка при регистрации критической ошибки: {}", e.getMessage());
        }
    }

    /**
     * Категоризация исключения по коду
     */
    private void categorizeException(String errorCode) {
        if (errorCode == null)
            return;

        if (errorCode.startsWith("VAL_")) {
            validationExceptions.incrementAndGet();
        } else if (errorCode.startsWith("SEC_")) {
            securityExceptions.incrementAndGet();
        } else if (errorCode.startsWith("BAL_") || errorCode.startsWith("TXN_")) {
            businessExceptions.incrementAndGet();
        }
    }

    /**
     * Получение общей статистики
     */
    public Map<String, Object> getOverallStatistics() {
        return Map.of(
                "totalExceptions", totalExceptions.get(),
                "criticalExceptions", criticalExceptions.get(),
                "validationExceptions", validationExceptions.get(),
                "securityExceptions", securityExceptions.get(),
                "businessExceptions", businessExceptions.get(),
                "lastExceptionTime", lastExceptionTime.toString(),
                "lastCriticalExceptionTime",
                lastCriticalExceptionTime != null ? lastCriticalExceptionTime.toString() : "none",
                "uniqueErrorCodes", exceptionCounts.size(),
                "uniquePaths", pathExceptionCounts.size());
    }

    /**
     * Получение топ ошибок по частоте
     */
    public Map<String, Long> getTopErrorCodes(int limit) {
        return exceptionCounts.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue().get(), e1.getValue().get()))
                .limit(limit)
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().get(),
                        (e1, e2) -> e1,
                        java.util.LinkedHashMap::new));
    }

    /**
     * Получение топ путей с ошибками
     */
    public Map<String, Long> getTopErrorPaths(int limit) {
        return pathExceptionCounts.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue().get(), e1.getValue().get()))
                .limit(limit)
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().get(),
                        (e1, e2) -> e1,
                        java.util.LinkedHashMap::new));
    }

    /**
     * Получение подробной статистики по типу ошибки
     */
    public Map<String, Object> getDetailedStatistics(String errorCode) {
        AtomicLong count = exceptionCounts.get(errorCode);
        if (count == null) {
            return Map.of("found", false);
        }

        double percentage = totalExceptions.get() > 0 ? (count.get() * 100.0) / totalExceptions.get() : 0.0;

        return Map.of(
                "found", true,
                "count", count.get(),
                "percentage", String.format("%.2f%%", percentage),
                "totalExceptions", totalExceptions.get());
    }

    /**
     * Сброс статистики
     */
    public void resetStatistics() {
        log.info("📊 Сброс статистики исключений. Было обработано {} исключений",
                totalExceptions.get());

        exceptionCounts.clear();
        pathExceptionCounts.clear();
        totalExceptions.set(0);
        criticalExceptions.set(0);
        validationExceptions.set(0);
        securityExceptions.set(0);
        businessExceptions.set(0);
        lastExceptionTime = LocalDateTime.now();
        lastCriticalExceptionTime = null;
    }

    /**
     * Проверка состояния метрик
     */
    public Map<String, Object> getHealthStatus() {
        long total = totalExceptions.get();
        long critical = criticalExceptions.get();

        String status;
        if (critical > 10) {
            status = "CRITICAL";
        } else if (critical > 5) {
            status = "WARNING";
        } else if (total > 1000) {
            status = "HIGH_LOAD";
        } else {
            status = "HEALTHY";
        }

        return Map.of(
                "status", status,
                "totalExceptions", total,
                "criticalExceptions", critical,
                "criticalPercentage", total > 0 ? (critical * 100.0) / total : 0.0,
                "lastActivity", lastExceptionTime.toString());
    }

    /**
     * Получение полного отчета для администратора
     */
    public Map<String, Object> getFullReport() {
        return Map.of(
                "overview", getOverallStatistics(),
                "health", getHealthStatus(),
                "topErrors", getTopErrorCodes(10),
                "topPaths", getTopErrorPaths(10),
                "reportTime", LocalDateTime.now().toString());
    }

    /**
     * Извлечение пути из запроса
     */
    private String extractPath(WebRequest request) {
        try {
            String description = request.getDescription(false);
            return description.replace("uri=", "");
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Логирование периодической статистики
     */
    public void logPeriodicStatistics() {
        try {
            if (totalExceptions.get() > 0) {
                log.info("📈 Статистика исключений: всего={}, критических={}, валидации={}, безопасности={}, бизнес={}",
                        totalExceptions.get(),
                        criticalExceptions.get(),
                        validationExceptions.get(),
                        securityExceptions.get(),
                        businessExceptions.get());

                // Топ-3 самых частых ошибок
                Map<String, Long> topErrors = getTopErrorCodes(3);
                if (!topErrors.isEmpty()) {
                    log.info("🔝 Топ ошибок: {}", topErrors);
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при логировании статистики: {}", e.getMessage());
        }
    }
}