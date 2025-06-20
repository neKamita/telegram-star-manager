package shit.back.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import shit.back.handler.CallbackHandler;
import shit.back.handler.MessageHandler;
import shit.back.entity.UserActivityLogEntity.ActionType;

import jakarta.annotation.PostConstruct;

@Service
@Profile("!production")
public class TelegramBotService extends TelegramLongPollingBot {
    private static final Logger logger = LoggerFactory.getLogger(TelegramBotService.class);

    private final String botToken;
    private final String botUsername;
    private final MessageHandler messageHandler;
    private final CallbackHandler callbackHandler;
    private final UserActivityLogService activityLogService;

    private boolean botRegistered = false;
    private String botStatus = "Not initialized";
    private String errorMessage = "";

    @Autowired
    public TelegramBotService(
            @Value("${telegram.bot.token:YOUR_BOT_TOKEN}") String botToken,
            @Value("${telegram.bot.username:StarManagerBot}") String botUsername,
            MessageHandler messageHandler,
            CallbackHandler callbackHandler,
            UserActivityLogService activityLogService) {
        super(getDefaultOptions());
        this.botToken = botToken;
        this.botUsername = botUsername;
        this.messageHandler = messageHandler;
        this.callbackHandler = callbackHandler;
        this.activityLogService = activityLogService;
    }

    private static DefaultBotOptions getDefaultOptions() {
        return new DefaultBotOptions();
    }

    @PostConstruct
    public void init() {
        logger.info("🤖 Initializing Telegram Bot Service...");

        if (botToken == null || botToken.isBlank() || "YOUR_BOT_TOKEN_HERE".equals(botToken)
                || "YOUR_BOT_TOKEN".equals(botToken)) {
            logger.warn("⚠️  Bot token not configured! Please set telegram.bot.token in application.properties");
            logger.warn("⚠️  Bot registration will be handled by TelegramBotConfig.");
            botRegistered = false;
            botStatus = "Not configured - missing bot token";
            errorMessage = "Bot token not configured";
            return;
        }

        deleteWebhookForPolling();

        botStatus = "Ready for registration";
        errorMessage = "";
        logger.info("🚀 Telegram Bot Service initialized. Registration will be handled by TelegramBotConfig.");
    }

    public void markAsRegistered() {
        botRegistered = true;
        botStatus = "Active and registered";
        errorMessage = "";
        logger.info("✅ Bot marked as successfully registered!");
    }

    public void markRegistrationFailed(String error) {
        botRegistered = false;
        botStatus = "Registration failed";
        errorMessage = error;
        logger.error("❌ Bot registration failed: {}", error);
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
            logger.error("Error processing update: {}", e.getMessage(), e);
        }
    }

    private void handleTextMessage(Update update) {
        Long userId = update.getMessage().getFrom().getId();
        String messageText = update.getMessage().getText();

        try {
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

            activityLogService.logTelegramBotActivity(
                    userId, username, firstName, lastName,
                    ActionType.BOT_MESSAGE_SENT,
                    "Bot replied to user message");

            logger.info("Sent message to user: {}", userId);
        } catch (TelegramApiException e) {
            String username = update.getMessage().getFrom().getUserName();
            String firstName = update.getMessage().getFrom().getFirstName();
            String lastName = update.getMessage().getFrom().getLastName();

            activityLogService.logTelegramBotActivity(
                    userId, username, firstName, lastName,
                    ActionType.PAYMENT_FAILED,
                    "Failed to send message: " + e.getMessage());
            logger.error("Error sending message: {}", e.getMessage(), e);
        }
    }

    private void handleCallbackQuery(Update update) {
        Long userId = update.getCallbackQuery().getFrom().getId();
        String callbackData = update.getCallbackQuery().getData();

        try {
            String username = update.getCallbackQuery().getFrom().getUserName();
            String firstName = update.getCallbackQuery().getFrom().getFirstName();
            String lastName = update.getCallbackQuery().getFrom().getLastName();

            activityLogService.logTelegramBotActivity(
                    userId, username, firstName, lastName,
                    ActionType.CALLBACK_RECEIVED,
                    "User clicked button: " + callbackData);

            EditMessageText editMessage = callbackHandler.handleCallback(update.getCallbackQuery());
            execute(editMessage);

            answerCallbackQuery(update.getCallbackQuery().getId());

            logger.info("Handled callback from user: {}", userId);
        } catch (TelegramApiException e) {
            String username = update.getCallbackQuery().getFrom().getUserName();
            String firstName = update.getCallbackQuery().getFrom().getFirstName();
            String lastName = update.getCallbackQuery().getFrom().getLastName();

            activityLogService.logTelegramBotActivity(
                    userId, username, firstName, lastName,
                    ActionType.PAYMENT_FAILED,
                    "Failed to handle callback: " + e.getMessage());
            logger.error("Error handling callback: {}", e.getMessage(), e);
        }
    }

    private void answerCallbackQuery(String callbackQueryId) {
        try {
            org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery answerCallbackQuery = new org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery();
            answerCallbackQuery.setCallbackQueryId(callbackQueryId);
            execute(answerCallbackQuery);
        } catch (TelegramApiException e) {
            logger.error("Error answering callback query: {}", e.getMessage(), e);
        }
    }

    public void sendMessage(Long chatId, String text) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText(text);
            message.setParseMode("HTML");
            execute(message);
            logger.info("Manual message sent to user: {}", chatId);
        } catch (TelegramApiException e) {
            logger.error("Error sending manual message: {}", e.getMessage(), e);
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
            logger.info("Manual message with keyboard sent to user: {}", chatId);
        } catch (TelegramApiException e) {
            logger.error("Error sending manual message with keyboard: {}", e.getMessage(), e);
        }
    }

    /**
     * Удаляет webhook для обеспечения корректной работы polling в dev режиме
     */
    private void deleteWebhookForPolling() {
        try {
            logger.info("🔄 Удаление webhook для обеспечения работы polling в dev режиме...");

            DeleteWebhook deleteWebhook = new DeleteWebhook();
            deleteWebhook.setDropPendingUpdates(true);

            TelegramLongPollingBot tempBot = new TelegramLongPollingBot(getDefaultOptions()) {
                @Override
                public String getBotUsername() {
                    return botUsername;
                }

                @Override
                public String getBotToken() {
                    return botToken;
                }

                @Override
                public void onUpdateReceived(Update update) {
                    // Не используется
                }
            };

            Boolean result = tempBot.execute(deleteWebhook);

            if (Boolean.TRUE.equals(result)) {
                logger.info("✅ Webhook успешно удален, polling может работать");
            } else {
                logger.warn("⚠️ Webhook не был удален, но это может быть нормально если его не было");
            }

        } catch (TelegramApiException e) {
            if (e.getMessage() != null && e.getMessage().contains("Bad Request: webhook is not set")) {
                logger.info("ℹ️ Webhook не был установлен, продолжаем с polling");
            } else {
                logger.warn("⚠️ Не удалось удалить webhook: {}. Polling может не работать.", e.getMessage());
                logger.warn("💡 Решение: вручную удалите webhook через Bot API или дождитесь истечения его срока");
            }
        } catch (Exception e) {
            logger.error("❌ Неожиданная ошибка при удалении webhook: {}", e.getMessage(), e);
        }
    }

    @Override
    public void onRegister() {
        logger.info("Bot registered successfully: {}", botUsername);
    }

    // Геттеры для статусов
    public boolean isBotRegistered() {
        return botRegistered;
    }

    public String getBotStatus() {
        return botStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
