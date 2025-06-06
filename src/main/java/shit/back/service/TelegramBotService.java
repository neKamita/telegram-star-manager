package shit.back.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import shit.back.handler.CallbackHandler;
import shit.back.handler.MessageHandler;

import jakarta.annotation.PostConstruct;

@Slf4j
@Service
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
    private TelegramBotsApi telegramBotsApi;
    
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
            log.warn("⚠️  Bot registration skipped. The application will start but the bot won't work.");
            botRegistered = false;
            botStatus = "Not configured - missing bot token";
            errorMessage = "Bot token not configured";
            return;
        }
        
        try {
            log.info("🔗 Attempting to register bot with username: {}", getBotUsername());
            telegramBotsApi.registerBot(this);
            
            // Success
            botRegistered = true;
            botStatus = "Active and registered";
            errorMessage = "";
            log.info("✅ Telegram bot '{}' registered successfully!", getBotUsername());
            
        } catch (TelegramApiException e) {
            // Graceful error handling - don't crash the application
            botRegistered = false;
            botStatus = "Registration failed";
            errorMessage = e.getMessage();
            
            log.error("❌ Error registering Telegram bot: {}", e.getMessage());
            log.error("💡 Please check your bot token and username in .env file");
            log.warn("⚠️  Application will continue to run, but Telegram bot features will be unavailable");
            log.warn("⚠️  REST API endpoints will work normally");
            
            // Don't throw exception - let application continue
        } catch (Exception e) {
            // Handle any other unexpected errors
            botRegistered = false;
            botStatus = "Unexpected error";
            errorMessage = e.getMessage();
            
            log.error("❌ Unexpected error during bot initialization: {}", e.getMessage(), e);
            log.warn("⚠️  Application will continue to run without Telegram bot functionality");
        }
        
        log.info("🚀 Telegram Bot Service initialization completed. Status: {}", botStatus);
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
        try {
            SendMessage message = messageHandler.handleMessage(update.getMessage());
            execute(message);
            log.info("Sent message to user: {}", update.getMessage().getFrom().getId());
        } catch (TelegramApiException e) {
            log.error("Error sending message: {}", e.getMessage(), e);
        }
    }
    
    private void handleCallbackQuery(Update update) {
        try {
            EditMessageText editMessage = callbackHandler.handleCallback(update.getCallbackQuery());
            execute(editMessage);
            
            // Отвечаем на callback query
            answerCallbackQuery(update.getCallbackQuery().getId());
            
            log.info("Handled callback from user: {}", update.getCallbackQuery().getFrom().getId());
        } catch (TelegramApiException e) {
            log.error("Error handling callback: {}", e.getMessage(), e);
        }
    }
    
    private void answerCallbackQuery(String callbackQueryId) {
        try {
            org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery answerCallbackQuery = 
                new org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery();
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
    
    public void sendMessageWithKeyboard(Long chatId, String text, org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup keyboard) {
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
    
    @Override
    public void onRegister() {
        log.info("Bot registered successfully: {}", getBotUsername());
    }
}
