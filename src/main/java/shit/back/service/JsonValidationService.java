package shit.back.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ДИАГНОСТИЧЕСКИЙ СЕРВИС: Валидация JSON для предотвращения parsing ошибок
 * Специализированный сервис для проверки и исправления JSON в SSE событиях
 */
@Service
public class JsonValidationService {

    private static final Logger log = LoggerFactory.getLogger(JsonValidationService.class);

    // Глобальный ObjectMapper из JacksonConfig
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Валидация JSON строки на корректность
     */
    public boolean isValidJson(String jsonString) {
        try {
            objectMapper.readTree(jsonString);
            return true;
        } catch (Exception e) {
            log.error("❌ JSON ВАЛИДАЦИЯ: Некорректный JSON: {}", e.getMessage());
            log.debug("❌ JSON ВАЛИДАЦИЯ: Проблемный JSON: {}", jsonString);
            return false;
        }
    }

    /**
     * Исправление и валидация JSON для performance-metrics
     */
    public String validateAndFixPerformanceMetricsJson(Map<String, Object> metricsData) {
        try {
            // 🔍 ДИАГНОСТИКА ПОТЕРИ ПОЛЕЙ: Логируем входящие данные
            log.info("🔍 JSON VALIDATION ДИАГНОСТИКА: Входящие ключи metricsData ({}): {}",
                    metricsData.size(), metricsData.keySet());
            log.info("🔍 JSON VALIDATION ДИАГНОСТИКА: Проверяем новые поля ДО валидации:");
            log.info("🔍 averageConnectionAcquisitionTimeMs = {}",
                    metricsData.get("averageConnectionAcquisitionTimeMs"));
            log.info("🔍 totalConnectionRequests = {}", metricsData.get("totalConnectionRequests"));
            log.info("🔍 connectionLeaksDetected = {}", metricsData.get("connectionLeaksDetected"));
            log.info("🔍 connectionPoolPerformanceLevel = {}", metricsData.get("connectionPoolPerformanceLevel"));
            log.info("🔍 connectionPoolEfficiency = {}", metricsData.get("connectionPoolEfficiency"));

            // Создаем копию данных для безопасности
            Map<String, Object> safeData = new LinkedHashMap<>();

            // Валидируем и очищаем каждое поле
            safeData.put("responseTime", validateNumericField(metricsData.get("responseTime"), 100.0));
            safeData.put("averageResponseTime", validateNumericField(metricsData.get("averageResponseTime"), 100.0));
            safeData.put("memoryUsage", validateIntegerField(metricsData.get("memoryUsage"), 70));
            safeData.put("memoryUsagePercent", validateIntegerField(metricsData.get("memoryUsagePercent"), 70));
            safeData.put("cacheHitRatio", validateIntegerField(metricsData.get("cacheHitRatio"), 90));
            safeData.put("totalUsers", validateLongField(metricsData.get("totalUsers"), 0L));
            safeData.put("activeUsers", validateLongField(metricsData.get("activeUsers"), 0L));
            safeData.put("onlineUsers", validateLongField(metricsData.get("onlineUsers"), 0L));
            safeData.put("totalOrders", validateLongField(metricsData.get("totalOrders"), 0L));
            safeData.put("healthScore", validateIntegerField(metricsData.get("healthScore"), 80));
            safeData.put("uptime", validateLongField(metricsData.get("uptime"), 3600L));
            safeData.put("timestamp", validateTimestampField(metricsData.get("timestamp")));
            safeData.put("source", validateStringField(metricsData.get("source"), "validated-source"));
            safeData.put("collectionNumber", validateLongField(metricsData.get("collectionNumber"), 1L));
            safeData.put("success", true);

            // КРИТИЧНЫЕ НОВЫЕ ПОЛЯ Database & Cache - ДИАГНОСТИКА
            Object dbPoolUsageInput = metricsData.get("dbPoolUsage");
            Object cacheMissRatioInput = metricsData.get("cacheMissRatio");
            Object activeDbConnectionsInput = metricsData.get("activeDbConnections");

            log.error("🚨 JSON VALIDATION КРИТИЧЕСКАЯ ДИАГНОСТИКА: Input DB cache fields:");
            log.error("🚨 dbPoolUsageInput = {} (type: {})", dbPoolUsageInput,
                    dbPoolUsageInput != null ? dbPoolUsageInput.getClass().getSimpleName() : "null");
            log.error("🚨 cacheMissRatioInput = {} (type: {})", cacheMissRatioInput,
                    cacheMissRatioInput != null ? cacheMissRatioInput.getClass().getSimpleName() : "null");
            log.error("🚨 activeDbConnectionsInput = {} (type: {})", activeDbConnectionsInput,
                    activeDbConnectionsInput != null ? activeDbConnectionsInput.getClass().getSimpleName() : "null");

            // КРИТИЧЕСКАЯ ПРОВЕРКА: если cacheMissRatioInput == null, то будет default = 10
            if (cacheMissRatioInput == null) {
                log.error("🚨 НАЙДЕН ИСТОЧНИК ПРОБЛЕМЫ: cacheMissRatioInput == NULL!");
                log.error("🚨 JsonValidationService будет использовать default значение 10, но откуда тогда 100%?");
            }

            Integer validatedDbPoolUsage = validateIntegerField(dbPoolUsageInput, 50);
            Integer validatedCacheMissRatio = validateIntegerField(cacheMissRatioInput, 10);
            Integer validatedActiveDbConnections = validateIntegerField(activeDbConnectionsInput, 3);

            safeData.put("dbPoolUsage", validatedDbPoolUsage);
            safeData.put("cacheMissRatio", validatedCacheMissRatio);
            safeData.put("activeDbConnections", validatedActiveDbConnections);

            log.error("🚨 JSON VALIDATION КРИТИЧЕСКАЯ ДИАГНОСТИКА: Output validated fields:");
            log.error("🚨 validatedDbPoolUsage = {}", validatedDbPoolUsage);
            log.error("🚨 validatedCacheMissRatio = {} (ЭТО КРИТИЧНО!)", validatedCacheMissRatio);
            log.error("🚨 validatedActiveDbConnections = {}", validatedActiveDbConnections);

            // ПРОВЕРЯЕМ: если validatedCacheMissRatio = 10, но в UI приходит 100, значит
            // проблема в другом месте!
            if (validatedCacheMissRatio != null && validatedCacheMissRatio == 10) {
                log.error("🎯 JsonValidationService возвращает КОРРЕКТНОЕ cacheMissRatio = 10% - проблема НЕ здесь!");
            } else if (validatedCacheMissRatio != null && validatedCacheMissRatio >= 90) {
                log.error("🚨 JsonValidationService возвращает ВЫСОКОЕ cacheMissRatio = {}% - ПРОБЛЕМА НАЙДЕНА!",
                        validatedCacheMissRatio);
            }

            // ДОБАВЛЯЕМ НОВЫЕ CONNECTION POOL ПОЛЯ В SAFEDATA
            Object avgConnectionAcquisitionTimeInput = metricsData.get("averageConnectionAcquisitionTimeMs");
            Object totalConnectionRequestsInput = metricsData.get("totalConnectionRequests");
            Object connectionLeaksDetectedInput = metricsData.get("connectionLeaksDetected");
            Object connectionPoolPerformanceLevelInput = metricsData.get("connectionPoolPerformanceLevel");
            Object connectionPoolEfficiencyInput = metricsData.get("connectionPoolEfficiency");

            log.info("🔍 JSON VALIDATION ДИАГНОСТИКА: Connection Pool поля до валидации:");
            log.info("🔍 averageConnectionAcquisitionTimeMs = {}", avgConnectionAcquisitionTimeInput);
            log.info("🔍 totalConnectionRequests = {}", totalConnectionRequestsInput);
            log.info("🔍 connectionLeaksDetected = {}", connectionLeaksDetectedInput);
            log.info("🔍 connectionPoolPerformanceLevel = {}", connectionPoolPerformanceLevelInput);
            log.info("🔍 connectionPoolEfficiency = {}", connectionPoolEfficiencyInput);

            // Connection Pool расширенные метрики с валидацией типов
            safeData.put("averageConnectionAcquisitionTimeMs",
                    validateNumericField(avgConnectionAcquisitionTimeInput, 0.0));
            safeData.put("totalConnectionRequests",
                    validateLongField(totalConnectionRequestsInput, 0L));
            safeData.put("connectionLeaksDetected",
                    validateIntegerField(connectionLeaksDetectedInput, 0));
            safeData.put("connectionPoolPerformanceLevel",
                    validateStringField(connectionPoolPerformanceLevelInput, "UNKNOWN"));
            safeData.put("connectionPoolEfficiency",
                    validateNumericField(connectionPoolEfficiencyInput, 0.0));

            // 🔍 ИСПРАВЛЕННАЯ ДИАГНОСТИКА: Проверяем что safeData ТЕПЕРЬ содержит новые
            // поля
            log.info("🔍 JSON VALIDATION ДИАГНОСТИКА: safeData ключи ПОСЛЕ добавления новых полей ({}): {}",
                    safeData.size(), safeData.keySet());
            log.info("✅ JSON VALIDATION ДИАГНОСТИКА: Новые поля ДОБАВЛЕНЫ в safeData:");
            log.info("✅ averageConnectionAcquisitionTimeMs в safeData = {}",
                    safeData.get("averageConnectionAcquisitionTimeMs"));
            log.info("✅ totalConnectionRequests в safeData = {}", safeData.get("totalConnectionRequests"));
            log.info("✅ connectionLeaksDetected в safeData = {}", safeData.get("connectionLeaksDetected"));
            log.info("✅ connectionPoolPerformanceLevel в safeData = {}",
                    safeData.get("connectionPoolPerformanceLevel"));
            log.info("✅ connectionPoolEfficiency в safeData = {}", safeData.get("connectionPoolEfficiency"));

            log.info(
                    "✅ JSON VALIDATION ИСПРАВЛЕНО: JsonValidationService теперь сохраняет новые поля! Размер входящих данных: {}, размер safeData: {}",
                    metricsData.size(), safeData.size());

            // Сериализуем в JSON
            String jsonString = objectMapper.writeValueAsString(safeData);

            // Дополнительная валидация
            if (!isValidJson(jsonString)) {
                throw new RuntimeException("JSON validation failed after creation");
            }

            log.debug("✅ JSON ВАЛИДАЦИЯ: Performance metrics JSON прошел валидацию");
            return jsonString;

        } catch (Exception e) {
            log.error("❌ JSON ВАЛИДАЦИЯ: Ошибка при валидации performance metrics: {}", e.getMessage(), e);
            return createFallbackPerformanceMetricsJson();
        }
    }

    /**
     * Валидация числового поля
     */
    private Double validateNumericField(Object value, Double defaultValue) {
        if (value == null)
            return defaultValue;

        try {
            if (value instanceof Number) {
                double result = ((Number) value).doubleValue();
                // Проверяем на NaN и бесконечность
                if (Double.isNaN(result) || Double.isInfinite(result)) {
                    return defaultValue;
                }
                return Math.round(result * 10.0) / 10.0; // Округляем до 1 знака
            }
            return Double.parseDouble(value.toString());
        } catch (Exception e) {
            log.debug("Invalid numeric field: {}, using default: {}", value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Валидация целочисленного поля
     */
    private Integer validateIntegerField(Object value, Integer defaultValue) {
        if (value == null)
            return defaultValue;

        try {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            log.debug("Invalid integer field: {}, using default: {}", value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Валидация long поля
     */
    private Long validateLongField(Object value, Long defaultValue) {
        if (value == null)
            return defaultValue;

        try {
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            log.debug("Invalid long field: {}, using default: {}", value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Валидация строкового поля
     */
    private String validateStringField(Object value, String defaultValue) {
        if (value == null)
            return defaultValue;

        String stringValue = value.toString();
        // Удаляем потенциально опасные символы для JSON
        return stringValue.replaceAll("[\"'\\\\\\r\\n\\t]", "").trim();
    }

    /**
     * Валидация timestamp поля
     */
    private String validateTimestampField(Object value) {
        try {
            if (value instanceof LocalDateTime) {
                return ((LocalDateTime) value).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } else if (value instanceof String) {
                String stringValue = (String) value;
                // Проверяем, что строка не содержит кавычек и спецсимволов
                return stringValue.replaceAll("[\"'\\\\]", "");
            }
        } catch (Exception e) {
            log.debug("Invalid timestamp field: {}", value);
        }

        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    /**
     * Создание fallback JSON для performance metrics
     */
    private String createFallbackPerformanceMetricsJson() {
        try {
            Map<String, Object> fallbackData = new LinkedHashMap<>();
            fallbackData.put("responseTime", 100.0);
            fallbackData.put("averageResponseTime", 100.0);
            fallbackData.put("memoryUsage", 70);
            fallbackData.put("memoryUsagePercent", 70);
            fallbackData.put("cacheHitRatio", 80);
            fallbackData.put("totalUsers", 0L);
            fallbackData.put("activeUsers", 0L);
            fallbackData.put("onlineUsers", 0L);
            fallbackData.put("totalOrders", 0L);
            fallbackData.put("healthScore", 50);
            fallbackData.put("uptime", 3600L);
            fallbackData.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            fallbackData.put("source", "fallback-validation-error");
            fallbackData.put("collectionNumber", 0L);
            fallbackData.put("success", false);
            fallbackData.put("error", "JSON validation failed");

            // НОВЫЕ FALLBACK ЗНАЧЕНИЯ для Database & Cache
            fallbackData.put("dbPoolUsage", 50);
            fallbackData.put("cacheMissRatio", 10);
            fallbackData.put("activeDbConnections", 3);

            // НОВЫЕ FALLBACK ЗНАЧЕНИЯ для Connection Pool расширенных метрик
            fallbackData.put("averageConnectionAcquisitionTimeMs", 0.0);
            fallbackData.put("totalConnectionRequests", 0L);
            fallbackData.put("connectionLeaksDetected", 0);
            fallbackData.put("connectionPoolPerformanceLevel", "UNKNOWN");
            fallbackData.put("connectionPoolEfficiency", 0.0);

            return objectMapper.writeValueAsString(fallbackData);
        } catch (Exception e) {
            log.error("❌ JSON ВАЛИДАЦИЯ: Критическая ошибка создания fallback JSON: {}", e.getMessage());
            return "{\"error\":\"Critical JSON validation failure\",\"success\":false}";
        }
    }

    /**
     * Диагностический метод для логирования JSON ошибок
     */
    public void logJsonParsingError(String jsonString, Exception error) {
        log.error("❌ JSON PARSING ERROR: {}", error.getMessage());
        log.error("❌ ПРОБЛЕМНЫЙ JSON: {}",
                jsonString.length() > 500 ? jsonString.substring(0, 500) + "..." : jsonString);

        // Анализ возможной причины ошибки
        if (error.getMessage().contains("expected double-quoted property name")) {
            log.error("❌ ДИАГНОСТИКА: Вероятная причина - одинарные кавычки вместо двойных в именах свойств");
        }
        if (error.getMessage().contains("line") && error.getMessage().contains("column")) {
            log.error("❌ ДИАГНОСТИКА: Проверьте указанную позицию в JSON на наличие неэкранированных символов");
        }
    }
}