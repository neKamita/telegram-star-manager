package shit.back.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final ObjectMapper objectMapper;

    public JsonValidationService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

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

            log.info(
                    "🔍 JSON VALIDATION ДИАГНОСТИКА: Input DB fields - dbPoolUsage={}, cacheMissRatio={}, activeDbConnections={}",
                    dbPoolUsageInput, cacheMissRatioInput, activeDbConnectionsInput);

            safeData.put("dbPoolUsage", validateIntegerField(dbPoolUsageInput, 50));
            safeData.put("cacheMissRatio", validateIntegerField(cacheMissRatioInput, 10));
            safeData.put("activeDbConnections", validateIntegerField(activeDbConnectionsInput, 3));

            log.info(
                    "🔍 JSON VALIDATION ДИАГНОСТИКА: Output DB fields - dbPoolUsage={}, cacheMissRatio={}, activeDbConnections={}",
                    safeData.get("dbPoolUsage"), safeData.get("cacheMissRatio"), safeData.get("activeDbConnections"));

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