package shit.back.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramWebhookBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import shit.back.handler.MessageHandler;
import shit.back.handler.CallbackHandler;

import java.time.LocalDateTime;

@Service
@Profile("production")
public class TelegramWebhookBotService extends TelegramWebhookBot {

    private static final Logger logger = LoggerFactory.getLogger(TelegramWebhookBotService.class);

    public TelegramWebhookBotService() {
        logger.info("🎯 TelegramWebhookBotService CONSTRUCTOR вызван!");
        logger.info("📋 Active Profile: {}", System.getProperty("spring.profiles.active"));
    }

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.username}")
    private String botUsername;

    @Value("${telegram.bot.webhook-url}")
    private String webhookUrl;

    @Autowired(required = false)
    private MessageHandler messageHandler;

    @Autowired(required = false)
    private CallbackHandler callbackHandler;

    private boolean webhookSet = false;
    private String botStatus = "INITIALIZING";
    private String errorMessage = null;
    private LocalDateTime lastUpdate = LocalDateTime.now();

    /**
     * Инициализация webhook бота. Вызывается из TelegramBotRegistrationService.
     */
    public void init() {
        try {
            logger.info("🚀 НАЧАЛО инициализации TelegramWebhookBotService для production");
            logger.info("📋 Bot Token: {}",
                    botToken != null && !botToken.trim().isEmpty() ? "SET (" + botToken.length() + " chars)"
                            : "NOT_SET");
            logger.info("👤 Bot Username: {}", botUsername);
            logger.info("🌐 Webhook URL: {}", webhookUrl);
            logger.info("🔗 MessageHandler: {}", messageHandler != null ? "AVAILABLE" : "NULL");
            logger.info("🔗 CallbackHandler: {}", callbackHandler != null ? "AVAILABLE" : "NULL");

            if (botToken == null || botToken.trim().isEmpty()) {
                botStatus = "ERROR";
                errorMessage = "Bot token не настроен";
                logger.error("❌ Bot token не настроен!");
                return;
            }

            if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
                botStatus = "ERROR";
                errorMessage = "Webhook URL не настроен";
                logger.error("❌ Webhook URL не настроен! Значение: '{}'", webhookUrl);
                return;
            }

            if (messageHandler == null) {
                botStatus = "ERROR";
                errorMessage = "MessageHandler не инициализирован";
                logger.error("❌ MessageHandler не инициализирован!");
                return;
            }

            if (callbackHandler == null) {
                botStatus = "ERROR";
                errorMessage = "CallbackHandler не инициализирован";
                logger.error("❌ CallbackHandler не инициализирован!");
                return;
            }

            logger.info("✅ Все зависимости проверены, запускаем setupWebhook()");
            setupWebhook();
            logger.info("🏁 КОНЕЦ инициализации TelegramWebhookBotService");

        } catch (Exception e) {
            botStatus = "ERROR";
            errorMessage = "Ошибка при инициализации: " + e.getMessage();
            logger.error("💥 КРИТИЧЕСКАЯ ОШИБКА при инициализации TelegramWebhookBotService", e);
        }
    }

    private void setupWebhook() {
        try {
            String fullWebhookUrl = webhookUrl + "/webhook/telegram";
            SetWebhook setWebhook = SetWebhook.builder()
                    .url(fullWebhookUrl)
                    .build();

            logger.info("Настройка webhook: {}", fullWebhookUrl);

            Boolean result = execute(setWebhook);
            if (Boolean.TRUE.equals(result)) {
                webhookSet = true;
                botStatus = "RUNNING";
                errorMessage = null;
                logger.info("Webhook успешно установлен: {}", fullWebhookUrl);
            } else {
                botStatus = "ERROR";
                errorMessage = "Не удалось установить webhook";
                logger.error("Не удалось установить webhook");
            }
        } catch (TelegramApiException e) {
            botStatus = "ERROR";
            errorMessage = "Ошибка при установке webhook: " + e.getMessage();
            logger.error("Ошибка при установке webhook", e);
        }
    }

    @Override
    public BotApiMethod<?> onWebhookUpdateReceived(Update update) {
        try {
            lastUpdate = LocalDateTime.now();
            logger.info("🔄 onWebhookUpdateReceived: получен update ID {}", update.getUpdateId());

            if (update.hasMessage()) {
                Message message = update.getMessage();
                logger.info("📨 Обработка сообщения от пользователя {} (ID: {}): {}",
                        message.getFrom().getFirstName(), message.getFrom().getId(),
                        message.hasText() ? message.getText() : "[не текст]");

                if (messageHandler == null) {
                    logger.error("❌ MessageHandler is NULL! Не могу обработать сообщение");
                    return createErrorMessage(message.getChatId(), "Сервис временно недоступен");
                }

                BotApiMethod<?> response = messageHandler.handleMessage(message);
                logger.info("✅ MessageHandler вернул ответ: {}",
                        response != null ? response.getClass().getSimpleName() : "NULL");
                return response;
            }

            if (update.hasCallbackQuery()) {
                logger.info("🔘 Обработка callback от пользователя {} (ID: {}): {}",
                        update.getCallbackQuery().getFrom().getFirstName(),
                        update.getCallbackQuery().getFrom().getId(),
                        update.getCallbackQuery().getData());

                if (callbackHandler == null) {
                    logger.error("❌ CallbackHandler is NULL! Не могу обработать callback");
                    return null;
                }

                return callbackHandler.handleCallback(update.getCallbackQuery());
            }

            logger.warn("⚠️ Получен неподдерживаемый тип обновления: updateId={}, type={}",
                    update.getUpdateId(), getUpdateType(update));

        } catch (Exception e) {
            logger.error("💥 Критическая ошибка при обработке webhook update {}: {}",
                    update.getUpdateId(), e.getMessage(), e);

            // Возвращаем простое сообщение об ошибке, если можем определить chat_id
            if (update.hasMessage()) {
                return createErrorMessage(update.getMessage().getChatId(),
                        "Произошла ошибка при обработке сообщения. Попробуйте позже.");
            }
        }

        return null;
    }

    private SendMessage createErrorMessage(Long chatId, String errorText) {
        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(errorText)
                .build();
    }

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

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public String getBotPath() {
        return "/webhook/telegram";
    }

    // Методы для мониторинга состояния
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

    public void resetError() {
        if ("ERROR".equals(botStatus)) {
            errorMessage = null;
            setupWebhook();
        }
    }
}
