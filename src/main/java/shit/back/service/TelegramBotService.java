package shit.back.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import shit.back.handler.CallbackHandler;
import shit.back.handler.MessageHandler;
import shit.back.entity.UserActivityLogEntity.ActionType;

import jakarta.annotation.PostConstruct;

@Slf4j
@Service
@Profile("!production")
public class TelegramBotService extends TelegramLongPollingBot {

    @Value("${telegram.bot.token:YOUR_BOT_TOKEN}")
    private String botToken;

    @Value("${telegram.bot.username:StarManagerBot}")
    private String botUsername;

    @Autowired
    private MessageHandler messageHandler;

    @Autowired
    private CallbackHandler callbackHandler;

    @Autowired
    private UserActivityLogService activityLogService;

    // Status tracking fields
    @Getter
    private boolean botRegistered = false;

    @Getter
    private String botStatus = "Not initialized";

    @Getter
    private String errorMessage = "";

    @PostConstruct
    public void init() {
        log.info("🤖 Initializing Telegram Bot Service...");

        if ("YOUR_BOT_TOKEN_HERE".equals(botToken) || "YOUR_BOT_TOKEN".equals(botToken)) {
            log.warn("⚠️  Bot token not configured! Please set telegram.bot.token in application.properties");
            log.warn("⚠️  Bot registration will be handled by TelegramBotConfig.");
            botRegistered = false;
            botStatus = "Not configured - missing bot token";
            errorMessage = "Bot token not configured";
            return;
        }

        // Удаляем webhook для обеспечения корректной работы polling в dev режиме
        deleteWebhookForPolling();

        // Set status to ready - registration will be done by TelegramBotConfig
        botStatus = "Ready for registration";
        errorMessage = "";
        log.info("🚀 Telegram Bot Service initialized. Registration will be handled by TelegramBotConfig.");
    }

    // Method to be called by TelegramBotConfig after successful registration
    public void markAsRegistered() {
        botRegistered = true;
        botStatus = "Active and registered";
        errorMessage = "";
        log.info("✅ Bot marked as successfully registered!");
    }

    // Method to be called by TelegramBotConfig if registration fails
    public void markRegistrationFailed(String error) {
        botRegistered = false;
        botStatus = "Registration failed";
        errorMessage = error;
        log.error("❌ Bot registration failed: {}", error);
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                handleTextMessage(update);
            } else if (update.hasCallbackQuery()) {
                handleCallbackQuery(update);
            }
        } catch (Exception e) {
            log.error("Error processing update: {}", e.getMessage(), e);
        }
    }

    private void handleTextMessage(Update update) {
        Long userId = update.getMessage().getFrom().getId();
        String messageText = update.getMessage().getText();

        try {
            // Log incoming message
            String username = update.getMessage().getFrom().getUserName();
            String firstName = update.getMessage().getFrom().getFirstName();
            String lastName = update.getMessage().getFrom().getLastName();

            activityLogService.logTelegramBotActivity(
                    userId, username, firstName, lastName,
                    ActionType.USER_INPUT_RECEIVED,
                    "User sent message: "
                            + (messageText.length() > 50 ? messageText.substring(0, 50) + "..." : messageText));

            SendMessage message = messageHandler.handleMessage(update.getMessage());
            execute(message);

            // Log outgoing message
            activityLogService.logTelegramBotActivity(
                    userId, username, firstName, lastName,
                    ActionType.BOT_MESSAGE_SENT,
                    "Bot replied to user message");

            log.info("Sent message to user: {}", userId);
        } catch (TelegramApiException e) {
            // Log error
            String username = update.getMessage().getFrom().getUserName();
            String firstName = update.getMessage().getFrom().getFirstName();
            String lastName = update.getMessage().getFrom().getLastName();

            activityLogService.logTelegramBotActivity(
                    userId, username, firstName, lastName,
                    ActionType.PAYMENT_FAILED,
                    "Failed to send message: " + e.getMessage());
            log.error("Error sending message: {}", e.getMessage(), e);
        }
    }

    private void handleCallbackQuery(Update update) {
        Long userId = update.getCallbackQuery().getFrom().getId();
        String callbackData = update.getCallbackQuery().getData();

        try {
            // Log callback interaction
            String username = update.getCallbackQuery().getFrom().getUserName();
            String firstName = update.getCallbackQuery().getFrom().getFirstName();
            String lastName = update.getCallbackQuery().getFrom().getLastName();

            activityLogService.logTelegramBotActivity(
                    userId, username, firstName, lastName,
                    ActionType.CALLBACK_RECEIVED,
                    "User clicked button: " + callbackData);

            EditMessageText editMessage = callbackHandler.handleCallback(update.getCallbackQuery());
            execute(editMessage);

            // Отвечаем на callback query
            answerCallbackQuery(update.getCallbackQuery().getId());

            log.info("Handled callback from user: {}", userId);
        } catch (TelegramApiException e) {
            // Log callback error
            String username = update.getCallbackQuery().getFrom().getUserName();
            String firstName = update.getCallbackQuery().getFrom().getFirstName();
            String lastName = update.getCallbackQuery().getFrom().getLastName();

            activityLogService.logTelegramBotActivity(
                    userId, username, firstName, lastName,
                    ActionType.PAYMENT_FAILED,
                    "Failed to handle callback: " + e.getMessage());
            log.error("Error handling callback: {}", e.getMessage(), e);
        }
    }

    private void answerCallbackQuery(String callbackQueryId) {
        try {
            org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery answerCallbackQuery = new org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery();
            answerCallbackQuery.setCallbackQueryId(callbackQueryId);
            execute(answerCallbackQuery);
        } catch (TelegramApiException e) {
            log.error("Error answering callback query: {}", e.getMessage(), e);
        }
    }

    public void sendMessage(Long chatId, String text) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText(text);
            message.setParseMode("HTML");
            execute(message);
            log.info("Manual message sent to user: {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Error sending manual message: {}", e.getMessage(), e);
        }
    }

    public void sendMessageWithKeyboard(Long chatId, String text,
            org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup keyboard) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText(text);
            message.setParseMode("HTML");
            message.setReplyMarkup(keyboard);
            execute(message);
            log.info("Manual message with keyboard sent to user: {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Error sending manual message with keyboard: {}", e.getMessage(), e);
        }
    }

    /**
     * Удаляет webhook для обеспечения корректной работы polling в dev режиме
     */
    private void deleteWebhookForPolling() {
        try {
            log.info("🔄 Удаление webhook для обеспечения работы polling в dev режиме...");

            DeleteWebhook deleteWebhook = new DeleteWebhook();
            deleteWebhook.setDropPendingUpdates(true); // Удаляем также все pending updates

            // Создаем временный экземпляр для выполнения команды
            TelegramLongPollingBot tempBot = new TelegramLongPollingBot() {
                @Override
                public String getBotUsername() {
                    return TelegramBotService.this.getBotUsername();
                }

                @Override
                public String getBotToken() {
                    return TelegramBotService.this.getBotToken();
                }

                @Override
                public void onUpdateReceived(Update update) {
                    // Не используется
                }
            };

            Boolean result = tempBot.execute(deleteWebhook);

            if (Boolean.TRUE.equals(result)) {
                log.info("✅ Webhook успешно удален, polling может работать");
            } else {
                log.warn("⚠️ Webhook не был удален, но это может быть нормально если его не было");
            }

        } catch (TelegramApiException e) {
            // Это может быть нормально, если webhook не был установлен
            if (e.getMessage().contains("Bad Request: webhook is not set")) {
                log.info("ℹ️ Webhook не был установлен, продолжаем с polling");
            } else {
                log.warn("⚠️ Не удалось удалить webhook: {}. Polling может не работать.", e.getMessage());
                log.warn("💡 Решение: вручную удалите webhook через Bot API или дождитесь истечения его срока");
            }
        } catch (Exception e) {
            log.error("❌ Неожиданная ошибка при удалении webhook: {}", e.getMessage(), e);
        }
    }

    @Override
    public void onRegister() {
        log.info("Bot registered successfully: {}", getBotUsername());
    }
}
