package shit.back.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import shit.back.service.JsonValidationService;
import shit.back.service.BackgroundMetricsService;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ДИАГНОСТИЧЕСКИЙ КОНТРОЛЛЕР: Для тестирования JSON валидности и исправлений
 * Специализированный контроллер для диагностики проблем с JSON parsing в SSE
 * событиях
 */
@Slf4j
@RestController
@RequestMapping("/admin/api/json-diagnostic")
public class JsonDiagnosticController {

    @Autowired
    private JsonValidationService jsonValidationService;

    @Autowired
    private BackgroundMetricsService backgroundMetricsService;

    /**
     * ДИАГНОСТИКА: Тест валидности JSON для performance-metrics
     */
    @GetMapping(value = "/test-performance-metrics-json", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> testPerformanceMetricsJson() {
        try {
            log.info("🔍 JSON ДИАГНОСТИКА: Тестирование JSON валидности для performance-metrics");

            // Создаем тестовые данные метрик
            Map<String, Object> testMetrics = createTestPerformanceMetrics();

            // Тестируем валидацию через JsonValidationService
            String validatedJson = jsonValidationService.validateAndFixPerformanceMetricsJson(testMetrics);

            // Проверяем валидность результата
            boolean isValid = jsonValidationService.isValidJson(validatedJson);

            return Map.of(
                    "status", isValid ? "JSON_VALID" : "JSON_INVALID",
                    "message", "Performance metrics JSON validation test completed",
                    "originalData", testMetrics,
                    "validatedJson", validatedJson,
                    "isValid", isValid,
                    "jsonLength", validatedJson.length(),
                    "timestamp", LocalDateTime.now(),
                    "success", true);

        } catch (Exception e) {
            log.error("❌ JSON ДИАГНОСТИКА: Ошибка тестирования JSON: {}", e.getMessage(), e);
            return Map.of(
                    "status", "JSON_TEST_ERROR",
                    "error", e.getMessage(),
                    "message", "JSON validation test failed",
                    "timestamp", LocalDateTime.now(),
                    "success", false);
        }
    }

    /**
     * ДИАГНОСТИКА: Тест проблемных символов в JSON
     */
    @GetMapping(value = "/test-problematic-characters", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> testProblematicCharacters() {
        try {
            log.info("🔍 JSON ДИАГНОСТИКА: Тестирование проблемных символов в JSON");

            // Создаем данные с потенциально проблемными символами
            Map<String, Object> problematicData = new LinkedHashMap<>();
            problematicData.put("responseTime", 75.5);
            problematicData.put("source", "test'source\"with\\problematic/characters");
            problematicData.put("timestamp", "2025-01-25T23:27:00'T'invalid");
            problematicData.put("message", "Test message with \"quotes\" and 'apostrophes'");

            // Тестируем валидацию
            String validatedJson = jsonValidationService.validateAndFixPerformanceMetricsJson(problematicData);
            boolean isValid = jsonValidationService.isValidJson(validatedJson);

            return Map.of(
                    "status", "PROBLEMATIC_CHARS_TEST",
                    "message", "Test completed for problematic characters",
                    "originalData", problematicData,
                    "validatedJson", validatedJson,
                    "isValid", isValid,
                    "sanitized", isValid,
                    "timestamp", LocalDateTime.now(),
                    "success", true);

        } catch (Exception e) {
            log.error("❌ JSON ДИАГНОСТИКА: Ошибка тестирования проблемных символов: {}", e.getMessage(), e);
            return Map.of(
                    "status", "PROBLEMATIC_CHARS_ERROR",
                    "error", e.getMessage(),
                    "timestamp", LocalDateTime.now(),
                    "success", false);
        }
    }

    /**
     * ДИАГНОСТИКА: Симуляция ошибки JSON.parse: expected double-quoted property
     * name
     */
    @GetMapping(value = "/simulate-json-parse-error", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> simulateJsonParseError() {
        try {
            log.info("🔍 JSON ДИАГНОСТИКА: Симуляция ошибки JSON.parse");

            // Создаем намеренно некорректный JSON (как был до исправления)
            String invalidJson = "{\n    'responseTime': 75.5,\n    \"memoryUsage\": 70,\n    timestamp: \"2025-01-25T23:27:00\"\n}";

            // Тестируем валидность
            boolean isValid = jsonValidationService.isValidJson(invalidJson);

            // Пытаемся исправить
            Map<String, Object> testData = Map.of(
                    "responseTime", 75.5,
                    "memoryUsage", 70,
                    "timestamp", "2025-01-25T23:27:00");

            String fixedJson = jsonValidationService.validateAndFixPerformanceMetricsJson(testData);
            boolean isFixedValid = jsonValidationService.isValidJson(fixedJson);

            return Map.of(
                    "status", "JSON_PARSE_ERROR_SIMULATION",
                    "message", "Simulation of JSON parse error completed",
                    "invalidJson", invalidJson,
                    "invalidJsonValid", isValid,
                    "fixedJson", fixedJson,
                    "fixedJsonValid", isFixedValid,
                    "errorFixed", !isValid && isFixedValid,
                    "timestamp", LocalDateTime.now(),
                    "success", true);

        } catch (Exception e) {
            log.error("❌ JSON ДИАГНОСТИКА: Ошибка симуляции JSON parse error: {}", e.getMessage(), e);
            return Map.of(
                    "status", "SIMULATION_ERROR",
                    "error", e.getMessage(),
                    "timestamp", LocalDateTime.now(),
                    "success", false);
        }
    }

    /**
     * ДИАГНОСТИКА: Получение статистики BackgroundMetricsService
     */
    @GetMapping(value = "/background-service-stats", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> getBackgroundServiceStats() {
        try {
            BackgroundMetricsService.BackgroundServiceStats stats = backgroundMetricsService.getServiceStats();

            return Map.of(
                    "status", "BACKGROUND_SERVICE_STATS",
                    "serviceStats", Map.of(
                            "totalCollections", stats.getTotalCollections(),
                            "lastCollectionDuration", stats.getLastCollectionDuration() + "ms",
                            "activeSSEConnections", stats.getActiveSSEConnections(),
                            "isHealthy", stats.getIsHealthy(),
                            "lastSuccessfulCollection", stats.getLastSuccessfulCollection()),
                    "timestamp", LocalDateTime.now(),
                    "success", true);

        } catch (Exception e) {
            log.error("❌ JSON ДИАГНОСТИКА: Ошибка получения статистики background service: {}", e.getMessage());
            return Map.of(
                    "status", "STATS_ERROR",
                    "error", e.getMessage(),
                    "timestamp", LocalDateTime.now(),
                    "success", false);
        }
    }

    /**
     * Создание тестовых данных performance metrics
     */
    private Map<String, Object> createTestPerformanceMetrics() {
        Map<String, Object> testData = new LinkedHashMap<>();
        testData.put("responseTime", 85.7);
        testData.put("averageResponseTime", 85.7);
        testData.put("memoryUsage", 72);
        testData.put("memoryUsagePercent", 72);
        testData.put("cacheHitRatio", 95);
        testData.put("totalUsers", 1250L);
        testData.put("activeUsers", 45L);
        testData.put("onlineUsers", 12L);
        testData.put("totalOrders", 8760L);
        testData.put("healthScore", 88);
        testData.put("uptime", 7200L);
        testData.put("timestamp", LocalDateTime.now());
        testData.put("source", "json-diagnostic-test");
        testData.put("collectionNumber", 999L);
        testData.put("success", true);

        return testData;
    }
}