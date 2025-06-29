package shit.back.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import shit.back.entity.UserActivityLogEntity;
import shit.back.repository.UserActivityLogJpaRepository;
import shit.back.service.BotSelfTestService;
import shit.back.service.TelegramWebhookBotService;

import jakarta.servlet.http.HttpServletRequest;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Объединенный диагностический контроллер
 * Включает функциональность всех diagnostic контроллеров
 * Следует принципам DRY, SOLID и Clean Code
 */
@Slf4j
@RestController
@RequestMapping("/diagnostic")
@RequiredArgsConstructor
public class UnifiedDiagnosticController {

    @Autowired(required = false)
    private TelegramWebhookBotService telegramBotService;

    @Autowired(required = false)
    private BotSelfTestService botSelfTestService;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    private final UserActivityLogJpaRepository activityLogRepository;
    private final DataSource dataSource;

    @Value("${telegram.bot.token:}")
    private String botToken;

    @Value("${telegram.bot.username:}")
    private String botUsername;

    @Value("${telegram.bot.webhook-url:}")
    private String webhookUrl;

    @Value("${spring.profiles.active:}")
    private String activeProfile;

    // ============= TELEGRAM ДИАГНОСТИКА =============

    /**
     * Комплексная диагностика Telegram бота
     */
    @GetMapping("/telegram-config")
    public ResponseEntity<Map<String, Object>> getTelegramConfig() {
        log.info("🔍 Запрос диагностики Telegram конфигурации");

        Map<String, Object> config = new HashMap<>();
        config.put("timestamp", System.currentTimeMillis());
        config.put("activeProfile", activeProfile);

        // Проверка токена
        Map<String, Object> tokenInfo = new HashMap<>();
        tokenInfo.put("isSet", botToken != null && !botToken.trim().isEmpty());
        tokenInfo.put("length", botToken != null ? botToken.length() : 0);
        tokenInfo.put("maskedValue", maskToken(botToken));
        config.put("botToken", tokenInfo);

        // Проверка имени бота
        Map<String, Object> usernameInfo = new HashMap<>();
        usernameInfo.put("isSet", botUsername != null && !botUsername.trim().isEmpty());
        usernameInfo.put("value", botUsername);
        config.put("botUsername", usernameInfo);

        // Проверка webhook URL
        Map<String, Object> webhookInfo = new HashMap<>();
        webhookInfo.put("isSet", webhookUrl != null && !webhookUrl.trim().isEmpty());
        webhookInfo.put("value", webhookUrl);
        config.put("webhookUrl", webhookInfo);

        // Проверка сервиса
        Map<String, Object> serviceInfo = new HashMap<>();
        serviceInfo.put("serviceExists", telegramBotService != null);
        if (telegramBotService != null) {
            serviceInfo.put("serviceClass", telegramBotService.getClass().getSimpleName());
            serviceInfo.put("isInitialized", true);
        }
        config.put("telegramService", serviceInfo);

        // Проверка бинов Spring
        Map<String, Object> beansInfo = new HashMap<>();
        beansInfo.put("telegramWebhookBotServiceExists",
                applicationContext.containsBean("telegramWebhookBotService"));
        config.put("springBeans", beansInfo);

        return ResponseEntity.ok(config);
    }

    /**
     * Запуск самотестирования бота
     */
    @GetMapping("/bot-self-test")
    public ResponseEntity<Map<String, Object>> runBotSelfTest() {
        log.info("🧪 Запрос запуска самотестирования бота");

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", System.currentTimeMillis());

        if (botSelfTestService == null) {
            response.put("status", "ERROR");
            response.put("message", "BotSelfTestService не доступен");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            CompletableFuture<BotSelfTestService.SelfTestResult> futureResult = botSelfTestService.performSelfTest();

            BotSelfTestService.SelfTestResult testResult = futureResult.get(30, java.util.concurrent.TimeUnit.SECONDS);

            response.put("status", testResult.isOverallSuccess() ? "SUCCESS" : "FAILURE");
            response.put("overallSuccess", testResult.isOverallSuccess());
            response.put("duration", testResult.getEndTime() - testResult.getStartTime());

            Map<String, Boolean> testResults = new HashMap<>();
            testResults.put("configuration", testResult.isConfigurationCheck());
            testResults.put("service", testResult.isServiceCheck());
            testResults.put("webhook", testResult.isWebhookCheck());
            testResults.put("messageTest", testResult.isMessageTestCheck());
            response.put("testResults", testResults);

            if (testResult.getErrorMessage() != null) {
                response.put("errorMessage", testResult.getErrorMessage());
            }

            return ResponseEntity.ok(response);

        } catch (java.util.concurrent.TimeoutException e) {
            response.put("status", "TIMEOUT");
            response.put("message", "Таймаут при выполнении самотестирования (30 сек)");
            return ResponseEntity.internalServerError().body(response);

        } catch (Exception e) {
            response.put("status", "ERROR");
            response.put("message", "Ошибка при самотестировании: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // ============= ENDPOINTS ДИАГНОСТИКА =============

    /**
     * Проверка всех зарегистрированных endpoints
     */
    @GetMapping("/endpoints")
    public ResponseEntity<Map<String, Object>> getAllEndpoints(HttpServletRequest request) {
        log.info("🔍 Запрос диагностики всех endpoints");

        Map<String, Object> result = new HashMap<>();

        try {
            Map<String, String> allMappings = requestMappingHandlerMapping
                    .getHandlerMethods()
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            entry -> entry.getKey().toString(),
                            entry -> entry.getValue().getMethod().getDeclaringClass().getSimpleName()
                                    + "." + entry.getValue().getMethod().getName()));

            result.put("totalEndpoints", allMappings.size());
            result.put("allMappings", allMappings);

            // Админские эндпоинты
            Map<String, String> adminEndpoints = allMappings.entrySet().stream()
                    .filter(entry -> entry.getKey().contains("/admin/"))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            result.put("adminEndpoints", adminEndpoints);
            result.put("adminEndpointsCount", adminEndpoints.size());

            // Критические эндпоинты
            result.put("hasDashboardDataEndpoint", allMappings.keySet().stream()
                    .anyMatch(key -> key.contains("dashboard-data")));
            result.put("hasSystemHealthEndpoint", allMappings.keySet().stream()
                    .anyMatch(key -> key.contains("system-health")));

            result.put("timestamp", LocalDateTime.now());
            result.put("success", true);

        } catch (Exception e) {
            log.error("🚨 Ошибка при получении списка endpoints", e);
            result.put("error", e.getMessage());
            result.put("success", false);
        }

        return ResponseEntity.ok(result);
    }

    // ============= ACTIVITY LOG ДИАГНОСТИКА =============

    /**
     * Проверка схемы таблицы user_activity_logs
     */
    @GetMapping("/activity-log/schema")
    public ResponseEntity<Map<String, Object>> checkActivityLogSchema() {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, String>> columns = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {

            log.info("=== Проверка схемы таблицы user_activity_logs ===");

            String sql = """
                    SELECT column_name, data_type, character_maximum_length, is_nullable
                    FROM information_schema.columns
                    WHERE table_name = 'user_activity_logs'
                    ORDER BY ordinal_position
                    """;

            ResultSet rs = statement.executeQuery(sql);

            while (rs.next()) {
                Map<String, String> column = new HashMap<>();
                String columnName = rs.getString("column_name");
                String dataType = rs.getString("data_type");
                String maxLength = rs.getString("character_maximum_length");
                String nullable = rs.getString("is_nullable");

                column.put("name", columnName);
                column.put("type", dataType);
                column.put("maxLength", maxLength);
                column.put("nullable", nullable);
                columns.add(column);

                // Проверяем проблемные поля
                if (Arrays.asList("username", "first_name", "last_name", "order_id", "action_details")
                        .contains(columnName)) {
                    if ("bytea".equals(dataType)) {
                        log.error("🚨 НАЙДЕНА ПРОБЛЕМА: Поле {} имеет тип bytea вместо text/varchar!", columnName);
                    } else {
                        log.info("✅ Поле {} имеет корректный тип: {}", columnName, dataType);
                    }
                }
            }

            result.put("columns", columns);
            result.put("status", "success");

        } catch (Exception e) {
            log.error("Ошибка при проверке схемы таблицы: ", e);
            result.put("error", e.getMessage());
            result.put("status", "error");
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Тестирование запросов к activity log
     */
    @GetMapping("/activity-log/test-query")
    public ResponseEntity<Map<String, Object>> testActivityLogQuery() {
        Map<String, Object> result = new HashMap<>();

        try {
            log.info("=== Тестирование запросов к activity log ===");

            List<UserActivityLogEntity> logs = activityLogRepository.findAll(PageRequest.of(0, 5)).getContent();
            log.info("✅ Запрос выполнился успешно. Найдено записей: {}", logs.size());

            result.put("recordsFound", logs.size());
            result.put("status", "success");
            result.put("message", "Запрос выполнен успешно");

        } catch (Exception e) {
            log.error("🚨 Ошибка при выполнении запроса: ", e);
            result.put("error", e.getMessage());
            result.put("status", "error");
        }

        return ResponseEntity.ok(result);
    }

    // ============= ОБЩИЕ ДИАГНОСТИЧЕСКИЕ МЕТОДЫ =============

    /**
     * Общая проверка здоровья системы
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        log.info("💚 Запрос общего состояния здоровья системы");

        Map<String, Object> health = new HashMap<>();
        health.put("timestamp", System.currentTimeMillis());
        health.put("status", "UP");
        health.put("activeProfile", activeProfile);

        // Проверка основных компонентов
        Map<String, String> components = new HashMap<>();
        components.put("application", "UP");
        components.put("telegramService", telegramBotService != null ? "UP" : "DOWN");
        components.put("springContext", applicationContext != null ? "UP" : "DOWN");
        health.put("components", components);

        // Проверка конфигурации
        Map<String, Boolean> configStatus = new HashMap<>();
        configStatus.put("botTokenConfigured", botToken != null && !botToken.trim().isEmpty());
        configStatus.put("webhookUrlConfigured", webhookUrl != null && !webhookUrl.trim().isEmpty());
        configStatus.put("botUsernameConfigured", botUsername != null && !botUsername.trim().isEmpty());
        health.put("configuration", configStatus);

        return ResponseEntity.ok(health);
    }

    /**
     * Тестовый endpoint для проверки работоспособности
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testEndpoint() {
        log.info("🧪 Тестовый endpoint вызван успешно!");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "OK");
        response.put("message", "Объединенный диагностический контроллер работает");
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    // ============= УТИЛИТНЫЕ МЕТОДЫ =============

    /**
     * Маскирование токена для безопасного отображения
     */
    private String maskToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return "NOT_SET";
        }
        if (token.length() <= 8) {
            return "***";
        }
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }
}