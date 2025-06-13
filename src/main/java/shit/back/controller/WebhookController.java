package shit.back.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;
import shit.back.service.TelegramWebhookBotService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@Profile("production")
@RequestMapping("/webhook")
public class WebhookController {

    private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);

    @Autowired
    private TelegramWebhookBotService telegramWebhookBotService;

    @PostMapping("/telegram")
    public ResponseEntity<BotApiMethod<?>> webhook(@RequestBody Update update) {
        try {
            // Безопасное логирование входящих обновлений с анонимизацией персональных
            // данных
            if (update.hasMessage()) {
                String maskedUserId = maskUserData(update.getMessage().getFrom().getId());
                String messageType = update.getMessage().hasText() ? "TEXT"
                        : update.getMessage().hasPhoto() ? "PHOTO"
                                : update.getMessage().hasDocument() ? "DOCUMENT" : "OTHER";

                logger.info("📨 Получено сообщение от пользователя [АНОНИМНО] (ID: {}): тип={}",
                        maskedUserId, messageType);
            } else if (update.hasCallbackQuery()) {
                String maskedUserId = maskUserData(update.getCallbackQuery().getFrom().getId());
                String callbackType = update.getCallbackQuery().getData() != null ? "DATA_CALLBACK" : "GAME_CALLBACK";

                logger.info("🔘 Получен callback от пользователя [АНОНИМНО] (ID: {}): тип={}",
                        maskedUserId, callbackType);
            } else {
                logger.info("📬 Получен webhook update ID: {} (тип: {})",
                        update.getUpdateId(), getUpdateType(update));
            }

            BotApiMethod<?> response = telegramWebhookBotService.onWebhookUpdateReceived(update);

            if (response != null) {
                logger.info("📤 Отправляется ответ: {}", response.getMethod());
                return ResponseEntity.ok(response);
            } else {
                logger.debug("✅ Обработка завершена без ответа для update: {}", update.getUpdateId());
                return ResponseEntity.ok().build();
            }

        } catch (Exception e) {
            logger.error("❌ Ошибка при обработке webhook update {}: {}",
                    update.getUpdateId(), e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Определение типа обновления для логирования
     */
    private String getUpdateType(Update update) {
        if (update.hasMessage())
            return "MESSAGE";
        if (update.hasCallbackQuery())
            return "CALLBACK";
        if (update.hasInlineQuery())
            return "INLINE_QUERY";
        if (update.hasEditedMessage())
            return "EDITED_MESSAGE";
        if (update.hasChannelPost())
            return "CHANNEL_POST";
        if (update.hasEditedChannelPost())
            return "EDITED_CHANNEL_POST";
        if (update.hasShippingQuery())
            return "SHIPPING_QUERY";
        if (update.hasPreCheckoutQuery())
            return "PRE_CHECKOUT_QUERY";
        if (update.hasPoll())
            return "POLL";
        if (update.hasPollAnswer())
            return "POLL_ANSWER";
        return "UNKNOWN";
    }

    /**
     * Анонимизация пользовательских данных для безопасного логирования
     * Использует SHA-256 хеширование для маскировки User ID
     * Соответствует требованиям GDPR/PDPA по защите персональных данных
     *
     * @param userId ID пользователя для анонимизации
     * @return Анонимизированный хеш (первые 8 символов SHA-256)
     */
    private String maskUserData(Long userId) {
        if (userId == null) {
            return "[НЕИЗВЕСТНО]";
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(userId.toString().getBytes(StandardCharsets.UTF_8));

            // Преобразуем в hex и берем первые 8 символов для идентификации в логах
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < Math.min(4, hash.length); i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return "HASH_" + hexString.toString().toUpperCase();

        } catch (NoSuchAlgorithmException e) {
            logger.error("Ошибка при анонимизации данных пользователя: {}", e.getMessage());
            return "[АНОНИМНО]";
        }
    }

    @GetMapping("/telegram/status")
    public ResponseEntity<WebhookStatus> getWebhookStatus() {
        try {
            WebhookStatus status = WebhookStatus.builder()
                    .webhookSet(telegramWebhookBotService.isWebhookSet())
                    .botStatus(telegramWebhookBotService.getBotStatus())
                    .errorMessage(telegramWebhookBotService.getErrorMessage())
                    .lastUpdate(telegramWebhookBotService.getLastUpdate())
                    .botUsername(telegramWebhookBotService.getBotUsername())
                    .webhookPath(telegramWebhookBotService.getBotPath())
                    .timestamp(LocalDateTime.now())
                    .build();

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            logger.error("Ошибка при получении статуса webhook", e);

            WebhookStatus errorStatus = WebhookStatus.builder()
                    .webhookSet(false)
                    .botStatus("ERROR")
                    .errorMessage("Ошибка при получении статуса: " + e.getMessage())
                    .lastUpdate(null)
                    .botUsername("UNKNOWN")
                    .webhookPath("/webhook/telegram")
                    .timestamp(LocalDateTime.now())
                    .build();

            return ResponseEntity.ok(errorStatus);
        }
    }

    @PostMapping("/telegram/reset")
    public ResponseEntity<Map<String, String>> resetWebhook() {
        try {
            logger.info("Запрос на сброс webhook");

            telegramWebhookBotService.resetError();

            Map<String, String> response = new HashMap<>();
            response.put("status", "RESET_REQUESTED");
            response.put("message", "Webhook сброшен, попытка переустановки");
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Ошибка при сбросе webhook", e);

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("status", "ERROR");
            errorResponse.put("message", "Ошибка при сбросе: " + e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    // Health check endpoint для Koyeb
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now());
        health.put("webhook", Map.of(
                "configured", telegramWebhookBotService.isWebhookSet(),
                "botStatus", telegramWebhookBotService.getBotStatus()));

        return ResponseEntity.ok(health);
    }

    // Test endpoint для симуляции /start команды
    @PostMapping("/telegram/test-start")
    public ResponseEntity<Map<String, Object>> testStartCommand() {
        try {
            logger.info("🧪 TEST: Симуляция команды /start для диагностики");

            Map<String, Object> result = new HashMap<>();
            result.put("status", "TEST_INITIATED");
            result.put("timestamp", LocalDateTime.now());
            result.put("message", "Тест временно отключен - используйте реальные сообщения в Telegram");
            result.put("instructions", Map.of(
                    "step1", "Напишите /start боту в Telegram",
                    "step2", "Проверьте логи приложения на сообщения с префиксом 📨",
                    "step3", "Если логи не появляются - проблема с webhook URL или токеном"));
            result.put("diagnostics", Map.of(
                    "webhookSet", telegramWebhookBotService.isWebhookSet(),
                    "botStatus", telegramWebhookBotService.getBotStatus(),
                    "errorMessage", telegramWebhookBotService.getErrorMessage(),
                    "lastUpdate", telegramWebhookBotService.getLastUpdate(),
                    "botUsername", telegramWebhookBotService.getBotUsername(),
                    "webhookPath", telegramWebhookBotService.getBotPath()));

            logger.info("📋 TEST: Текущее состояние бота - {}", telegramWebhookBotService.getBotStatus());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("❌ TEST: Ошибка при получении диагностической информации", e);

            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "ERROR");
            errorResult.put("timestamp", LocalDateTime.now());
            errorResult.put("error", e.getMessage());

            return ResponseEntity.internalServerError().body(errorResult);
        }
    }

    // Data class для статуса webhook
    public static class WebhookStatus {
        private boolean webhookSet;
        private String botStatus;
        private String errorMessage;
        private LocalDateTime lastUpdate;
        private String botUsername;
        private String webhookPath;
        private LocalDateTime timestamp;

        // Builder pattern
        public static WebhookStatusBuilder builder() {
            return new WebhookStatusBuilder();
        }

        public static class WebhookStatusBuilder {
            private boolean webhookSet;
            private String botStatus;
            private String errorMessage;
            private LocalDateTime lastUpdate;
            private String botUsername;
            private String webhookPath;
            private LocalDateTime timestamp;

            public WebhookStatusBuilder webhookSet(boolean webhookSet) {
                this.webhookSet = webhookSet;
                return this;
            }

            public WebhookStatusBuilder botStatus(String botStatus) {
                this.botStatus = botStatus;
                return this;
            }

            public WebhookStatusBuilder errorMessage(String errorMessage) {
                this.errorMessage = errorMessage;
                return this;
            }

            public WebhookStatusBuilder lastUpdate(LocalDateTime lastUpdate) {
                this.lastUpdate = lastUpdate;
                return this;
            }

            public WebhookStatusBuilder botUsername(String botUsername) {
                this.botUsername = botUsername;
                return this;
            }

            public WebhookStatusBuilder webhookPath(String webhookPath) {
                this.webhookPath = webhookPath;
                return this;
            }

            public WebhookStatusBuilder timestamp(LocalDateTime timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public WebhookStatus build() {
                WebhookStatus status = new WebhookStatus();
                status.webhookSet = this.webhookSet;
                status.botStatus = this.botStatus;
                status.errorMessage = this.errorMessage;
                status.lastUpdate = this.lastUpdate;
                status.botUsername = this.botUsername;
                status.webhookPath = this.webhookPath;
                status.timestamp = this.timestamp;
                return status;
            }
        }

        // Getters
        public boolean isWebhookSet() {
            return webhookSet;
        }

        public String getBotStatus() {
            return botStatus;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public LocalDateTime getLastUpdate() {
            return lastUpdate;
        }

        public String getBotUsername() {
            return botUsername;
        }

        public String getWebhookPath() {
            return webhookPath;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        // Setters
        public void setWebhookSet(boolean webhookSet) {
            this.webhookSet = webhookSet;
        }

        public void setBotStatus(String botStatus) {
            this.botStatus = botStatus;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public void setLastUpdate(LocalDateTime lastUpdate) {
            this.lastUpdate = lastUpdate;
        }

        public void setBotUsername(String botUsername) {
            this.botUsername = botUsername;
        }

        public void setWebhookPath(String webhookPath) {
            this.webhookPath = webhookPath;
        }

        public void setTimestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
        }
    }
}
