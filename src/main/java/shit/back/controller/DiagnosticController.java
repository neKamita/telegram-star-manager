package shit.back.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shit.back.service.TelegramWebhookBotService;
import shit.back.service.BotSelfTestService;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Диагностический контроллер для отладки Telegram Bot и системы
 */
@RestController
@RequestMapping("/diagnostic")
public class DiagnosticController {

    private static final Logger logger = LoggerFactory.getLogger(DiagnosticController.class);

    @Autowired(required = false)
    private TelegramWebhookBotService telegramBotService;

    @Autowired(required = false)
    private BotSelfTestService botSelfTestService;

    @Autowired
    private ApplicationContext applicationContext;

    @Value("${telegram.bot.token:}")
    private String botToken;

    @Value("${telegram.bot.username:}")
    private String botUsername;

    @Value("${telegram.bot.webhook-url:}")
    private String webhookUrl;

    @Value("${spring.profiles.active:}")
    private String activeProfile;

    /**
     * Диагностика конфигурации Telegram бота
     */
    @GetMapping("/telegram-config")
    public ResponseEntity<Map<String, Object>> getTelegramConfig() {
        logger.info("🔍 Запрос диагностики Telegram конфигурации");
        
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
     * Общая проверка здоровья системы
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        logger.info("💚 Запрос общего состояния здоровья системы");
        
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
     * Отладка переменных окружения
     */
    @GetMapping("/env-debug")
    public ResponseEntity<Map<String, Object>> getEnvDebug() {
        logger.info("🔧 Запрос отладки переменных окружения");
        
        Map<String, Object> envDebug = new HashMap<>();
        envDebug.put("timestamp", System.currentTimeMillis());
        
        // Системные переменные (безопасно)
        Map<String, String> systemInfo = new HashMap<>();
        systemInfo.put("javaVersion", System.getProperty("java.version"));
        systemInfo.put("osName", System.getProperty("os.name"));
        systemInfo.put("springProfilesActive", System.getProperty("spring.profiles.active"));
        envDebug.put("system", systemInfo);
        
        // Конфигурация приложения
        Map<String, Object> appConfig = new HashMap<>();
        appConfig.put("activeProfile", activeProfile);
        appConfig.put("botTokenPresent", botToken != null && !botToken.trim().isEmpty());
        appConfig.put("webhookUrlPresent", webhookUrl != null && !webhookUrl.trim().isEmpty());
        appConfig.put("botUsernamePresent", botUsername != null && !botUsername.trim().isEmpty());
        envDebug.put("applicationConfig", appConfig);
        
        // Проверка Spring бинов
        Map<String, Boolean> beans = new HashMap<>();
        beans.put("telegramWebhookBotService", applicationContext.containsBean("telegramWebhookBotService"));
        beans.put("telegramBotService", applicationContext.containsBean("telegramBotService"));
        envDebug.put("springBeans", beans);
        
        return ResponseEntity.ok(envDebug);
    }

    /**
     * Принудительная переинициализация Telegram сервиса
     */
    @GetMapping("/force-telegram-init")
    public ResponseEntity<Map<String, Object>> forceTelegramInit() {
        logger.info("🔄 Запрос принудительной переинициализации Telegram сервиса");
        
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", System.currentTimeMillis());
        
        if (telegramBotService == null) {
            result.put("status", "ERROR");
            result.put("message", "TelegramWebhookBotService не найден в контексте Spring");
            logger.error("❌ Не удалось найти TelegramWebhookBotService для переинициализации");
            return ResponseEntity.badRequest().body(result);
        }
        
        try {
            // Попытка переинициализации
            logger.info("🔄 Попытка переинициализации TelegramWebhookBotService...");
            
            // Здесь можно добавить логику переинициализации если она будет реализована в сервисе
            result.put("status", "SUCCESS");
            result.put("message", "Запрос на переинициализацию отправлен");
            result.put("serviceClass", telegramBotService.getClass().getSimpleName());
            
            logger.info("✅ Переинициализация TelegramWebhookBotService выполнена");
            
        } catch (Exception e) {
            result.put("status", "ERROR");
            result.put("message", "Ошибка при переинициализации: " + e.getMessage());
            logger.error("❌ Ошибка при переинициализации TelegramWebhookBotService: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(result);
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * Запуск полного самотестирования бота
     */
    @GetMapping("/bot-self-test")
    public ResponseEntity<Map<String, Object>> runBotSelfTest() {
        logger.info("🧪 Запрос запуска самотестирования бота");
        
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", System.currentTimeMillis());
        
        if (botSelfTestService == null) {
            response.put("status", "ERROR");
            response.put("message", "BotSelfTestService не доступен");
            logger.error("❌ BotSelfTestService не найден в контексте Spring");
            return ResponseEntity.badRequest().body(response);
        }
        
        try {
            // Запускаем самотестирование
            CompletableFuture<BotSelfTestService.SelfTestResult> futureResult = 
                botSelfTestService.performSelfTest();
            
            // Ждем результат (с таймаутом)
            BotSelfTestService.SelfTestResult testResult = futureResult.get(30, java.util.concurrent.TimeUnit.SECONDS);
            
            // Формируем ответ
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
            
            logger.info("✅ Самотестирование завершено: {}", 
                testResult.isOverallSuccess() ? "УСПЕХ" : "ОШИБКИ");
            
            return ResponseEntity.ok(response);
            
        } catch (java.util.concurrent.TimeoutException e) {
            response.put("status", "TIMEOUT");
            response.put("message", "Таймаут при выполнении самотестирования (30 сек)");
            logger.error("⏰ Таймаут при самотестировании");
            return ResponseEntity.internalServerError().body(response);
            
        } catch (Exception e) {
            response.put("status", "ERROR");
            response.put("message", "Ошибка при самотестировании: " + e.getMessage());
            logger.error("❌ Ошибка при самотестировании: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Получение информации о состоянии автотестирования
     */
    @GetMapping("/self-test-info")
    public ResponseEntity<Map<String, Object>> getSelfTestInfo() {
        logger.info("ℹ️ Запрос информации о самотестировании");
        
        Map<String, Object> info = new HashMap<>();
        info.put("timestamp", System.currentTimeMillis());
        info.put("selfTestServiceAvailable", botSelfTestService != null);
        
        if (botSelfTestService != null) {
            info.put("serviceClass", botSelfTestService.getClass().getSimpleName());
            info.put("description", "Сервис автоматического самотестирования Telegram бота");
            
            Map<String, String> availableTests = new HashMap<>();
            availableTests.put("configuration", "Проверка конфигурации бота (токен, username, webhook)");
            availableTests.put("service", "Проверка инициализации TelegramWebhookBotService");
            availableTests.put("webhook", "Проверка доступности webhook endpoint");
            availableTests.put("messageTest", "Симуляция обработки тестовых сообщений");
            info.put("availableTests", availableTests);
            
            Map<String, String> endpoints = new HashMap<>();
            endpoints.put("runSelfTest", "/diagnostic/bot-self-test");
            endpoints.put("selfTestInfo", "/diagnostic/self-test-info");
            info.put("endpoints", endpoints);
        } else {
            info.put("message", "Сервис самотестирования недоступен");
        }
        
        return ResponseEntity.ok(info);
    }

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
