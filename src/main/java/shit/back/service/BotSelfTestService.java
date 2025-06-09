package shit.back.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Сервис для автоматического самотестирования Telegram бота при запуске приложения
 */
@Slf4j
@Service
public class BotSelfTestService {

    @Autowired(required = false)
    private TelegramWebhookBotService telegramBotService;

    @Value("${telegram.bot.webhook-url:}")
    private String webhookUrl;

    @Value("${telegram.bot.token:}")
    private String botToken;

    @Value("${telegram.bot.username:}")
    private String botUsername;

    @Value("${telegram.self-test.enabled:true}")
    private boolean selfTestEnabled;

    @Value("${telegram.self-test.delay-seconds:10}")
    private int selfTestDelay;

    @Autowired
    @Qualifier("selfTestRestTemplate")
    private RestTemplate restTemplate;

    /**
     * Автоматический запуск self-test после инициализации приложения
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void onApplicationReady() {
        if (!selfTestEnabled) {
            log.info("🚫 Автотестирование бота отключено");
            return;
        }

        log.info("⏰ Запуск автотестирования бота через {} секунд...", selfTestDelay);
        
        try {
            // Ждем, чтобы все сервисы полностью инициализировались
            TimeUnit.SECONDS.sleep(selfTestDelay);
            
            performSelfTest();
            
        } catch (InterruptedException e) {
            log.warn("⚠️ Автотестирование прервано: {}", e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("❌ Ошибка при автотестировании: {}", e.getMessage(), e);
        }
    }

    /**
     * Выполнение полного самотестирования бота
     */
    public CompletableFuture<SelfTestResult> performSelfTest() {
        log.info("🧪 НАЧАЛО самотестирования Telegram бота");
        
        SelfTestResult result = new SelfTestResult();
        result.setStartTime(System.currentTimeMillis());

        try {
            // 1. Проверка конфигурации
            boolean configCheck = checkConfiguration(result);
            
            // 2. Проверка сервиса
            boolean serviceCheck = checkTelegramService(result);
            
            // 3. Проверка webhook
            boolean webhookCheck = checkWebhookConnectivity(result);
            
            // 4. Симуляция тестовых сообщений
            boolean messageTestCheck = simulateTestMessages(result);
            
            // Определение общего результата
            result.setOverallSuccess(configCheck && serviceCheck && webhookCheck && messageTestCheck);
            result.setEndTime(System.currentTimeMillis());
            
            logSelfTestResults(result);
            
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            result.setOverallSuccess(false);
            result.setErrorMessage("Критическая ошибка при самотестировании: " + e.getMessage());
            result.setEndTime(System.currentTimeMillis());
            
            log.error("💥 КРИТИЧЕСКАЯ ОШИБКА при самотестировании: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(result);
        }
    }

    /**
     * Проверка конфигурации бота
     */
    private boolean checkConfiguration(SelfTestResult result) {
        log.info("📋 Проверка конфигурации бота...");
        
        boolean tokenValid = botToken != null && !botToken.trim().isEmpty() && botToken.length() > 20;
        boolean usernameValid = botUsername != null && !botUsername.trim().isEmpty();
        boolean webhookValid = webhookUrl != null && !webhookUrl.trim().isEmpty() && webhookUrl.startsWith("https://");
        
        result.setConfigurationCheck(tokenValid && usernameValid && webhookValid);
        
        if (tokenValid) {
            log.info("✅ Bot Token: корректный ({} символов)", botToken.length());
        } else {
            log.error("❌ Bot Token: некорректный или отсутствует");
        }
        
        if (usernameValid) {
            log.info("✅ Bot Username: {}", botUsername);
        } else {
            log.error("❌ Bot Username: отсутствует");
        }
        
        if (webhookValid) {
            log.info("✅ Webhook URL: {}", webhookUrl);
        } else {
            log.error("❌ Webhook URL: некорректный или не HTTPS");
        }
        
        return result.isConfigurationCheck();
    }

    /**
     * Проверка Telegram сервиса
     */
    private boolean checkTelegramService(SelfTestResult result) {
        log.info("🤖 Проверка Telegram сервиса...");
        
        if (telegramBotService == null) {
            log.error("❌ TelegramWebhookBotService не инициализирован");
            result.setServiceCheck(false);
            return false;
        }
        
        try {
            boolean isInitialized = telegramBotService.isWebhookSet();
            String botStatus = telegramBotService.getBotStatus();
            String errorMessage = telegramBotService.getErrorMessage();
            
            // Исправлено: поддержка статусов RUNNING и ACTIVE
            boolean isStatusValid = "ACTIVE".equals(botStatus) || "RUNNING".equals(botStatus);
            result.setServiceCheck(isInitialized && isStatusValid);
            
            if (result.isServiceCheck()) {
                log.info("✅ Telegram сервис: инициализирован и активен");
                log.info("📊 Статус бота: {} (webhook установлен: {})", botStatus, isInitialized);
            } else {
                log.error("❌ Telegram сервис: проблема инициализации");
                log.error("📊 Webhook установлен: {}, Статус бота: {}", isInitialized, botStatus);
                if (errorMessage != null) {
                    log.error("📝 Ошибка: {}", errorMessage);
                }
            }
            
            return result.isServiceCheck();
            
        } catch (Exception e) {
            log.error("❌ Ошибка при проверке Telegram сервиса: {}", e.getMessage());
            result.setServiceCheck(false);
            return false;
        }
    }

    /**
     * Проверка доступности webhook endpoint
     */
    private boolean checkWebhookConnectivity(SelfTestResult result) {
        log.info("🌐 Проверка доступности webhook endpoint...");
        
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            log.error("❌ Webhook URL не настроен");
            result.setWebhookCheck(false);
            return false;
        }
        
        try {
            // Исправлено: правильное формирование health endpoint
            // Если webhookUrl уже содержит путь /webhook/telegram, заменяем на /webhook/health
            // Если нет - добавляем /webhook/health
            String healthEndpoint;
            if (webhookUrl.contains("/webhook/telegram")) {
                healthEndpoint = webhookUrl.replace("/webhook/telegram", "/webhook/health");
            } else {
                healthEndpoint = webhookUrl + "/webhook/health";
            }
            
            log.debug("🔍 Проверяем health endpoint: {}", healthEndpoint);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                healthEndpoint, 
                HttpMethod.GET, 
                entity, 
                String.class
            );
            
            boolean isHealthy = response.getStatusCode().is2xxSuccessful();
            result.setWebhookCheck(isHealthy);
            
            if (isHealthy) {
                log.info("✅ Webhook endpoint доступен: {}", healthEndpoint);
                log.debug("📄 Health response: {}", response.getBody());
            } else {
                log.error("❌ Webhook endpoint недоступен: {} (статус: {})", 
                    healthEndpoint, response.getStatusCode());
            }
            
            return isHealthy;
            
        } catch (Exception e) {
            log.error("❌ Ошибка при проверке webhook connectivity: {}", e.getMessage());
            result.setWebhookCheck(false);
            return false;
        }
    }

    /**
     * Симуляция тестовых сообщений для проверки обработчиков
     */
    private boolean simulateTestMessages(SelfTestResult result) {
        log.info("📨 Симуляция тестовых сообщений...");
        
        try {
            // Создаем фиктивные webhook данные для тестирования
            Map<String, Object> testUpdate = createTestUpdate();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(testUpdate, headers);
            
            // Исправлено: правильное формирование webhook endpoint
            String webhookEndpoint;
            if (webhookUrl.contains("/webhook/telegram")) {
                webhookEndpoint = webhookUrl;
            } else {
                webhookEndpoint = webhookUrl + "/webhook/telegram";
            }
            
            log.debug("📡 Отправляем тестовое сообщение на: {}", webhookEndpoint);
            
            // Отправляем тестовое сообщение
            ResponseEntity<String> response = restTemplate.exchange(
                webhookEndpoint, 
                HttpMethod.POST, 
                entity, 
                String.class
            );
            
            boolean messageProcessed = response.getStatusCode().is2xxSuccessful();
            result.setMessageTestCheck(messageProcessed);
            
            if (messageProcessed) {
                log.info("✅ Тестовое сообщение обработано успешно");
            } else {
                log.error("❌ Ошибка при обработке тестового сообщения: {}", response.getStatusCode());
            }
            
            return messageProcessed;
            
        } catch (Exception e) {
            log.error("❌ Ошибка при симуляции тестовых сообщений: {}", e.getMessage());
            result.setMessageTestCheck(false);
            return false;
        }
    }

    /**
     * Создание тестового Update объекта
     */
    private Map<String, Object> createTestUpdate() {
        Map<String, Object> update = new HashMap<>();
        update.put("update_id", 999999);
        
        Map<String, Object> message = new HashMap<>();
        message.put("message_id", 1);
        message.put("date", System.currentTimeMillis() / 1000);
        message.put("text", "/start");
        
        Map<String, Object> chat = new HashMap<>();
        chat.put("id", -999999L);
        chat.put("type", "private");
        message.put("chat", chat);
        
        Map<String, Object> from = new HashMap<>();
        from.put("id", 999999L);
        from.put("is_bot", false);
        from.put("first_name", "SelfTest");
        from.put("username", "self_test_bot");
        message.put("from", from);
        
        update.put("message", message);
        
        return update;
    }

    /**
     * Логирование результатов самотестирования
     */
    private void logSelfTestResults(SelfTestResult result) {
        long duration = result.getEndTime() - result.getStartTime();
        
        log.info("🏁 ЗАВЕРШЕНИЕ самотестирования бота");
        log.info("⏱️ Общее время выполнения: {} мс", duration);
        log.info("📊 Общий результат: {}", result.isOverallSuccess() ? "✅ УСПЕХ" : "❌ ОШИБКА");
        
        log.info("📋 Детальные результаты:");
        log.info("  🔧 Конфигурация: {}", result.isConfigurationCheck() ? "✅" : "❌");
        log.info("  🤖 Сервис: {}", result.isServiceCheck() ? "✅" : "❌");
        log.info("  🌐 Webhook: {}", result.isWebhookCheck() ? "✅" : "❌");
        log.info("  📨 Сообщения: {}", result.isMessageTestCheck() ? "✅" : "❌");
        
        if (!result.isOverallSuccess() && result.getErrorMessage() != null) {
            log.error("📝 Ошибка: {}", result.getErrorMessage());
        }
        
        if (result.isOverallSuccess()) {
            log.info("🎉 Telegram бот готов к работе!");
        } else {
            log.warn("⚠️ Обнаружены проблемы с ботом. Проверьте логи выше.");
        }
    }

    /**
     * Результат самотестирования
     */
    public static class SelfTestResult {
        private long startTime;
        private long endTime;
        private boolean overallSuccess;
        private boolean configurationCheck;
        private boolean serviceCheck;
        private boolean webhookCheck;
        private boolean messageTestCheck;
        private String errorMessage;

        // Getters and Setters
        public long getStartTime() { return startTime; }
        public void setStartTime(long startTime) { this.startTime = startTime; }

        public long getEndTime() { return endTime; }
        public void setEndTime(long endTime) { this.endTime = endTime; }

        public boolean isOverallSuccess() { return overallSuccess; }
        public void setOverallSuccess(boolean overallSuccess) { this.overallSuccess = overallSuccess; }

        public boolean isConfigurationCheck() { return configurationCheck; }
        public void setConfigurationCheck(boolean configurationCheck) { this.configurationCheck = configurationCheck; }

        public boolean isServiceCheck() { return serviceCheck; }
        public void setServiceCheck(boolean serviceCheck) { this.serviceCheck = serviceCheck; }

        public boolean isWebhookCheck() { return webhookCheck; }
        public void setWebhookCheck(boolean webhookCheck) { this.webhookCheck = webhookCheck; }

        public boolean isMessageTestCheck() { return messageTestCheck; }
        public void setMessageTestCheck(boolean messageTestCheck) { this.messageTestCheck = messageTestCheck; }

        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
}
