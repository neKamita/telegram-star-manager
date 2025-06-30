package shit.back.telegram.ui.builder;

import shit.back.telegram.ui.TelegramUIResponse;
import shit.back.telegram.ui.factory.TelegramMessageFactory;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

/**
 * Builder для создания Telegram сообщений
 * 
 * Мигрирован из presentation layer с упрощением для новой архитектуры
 */
public class TelegramMessageBuilder {

    private final TelegramMessageFactory messageFactory;
    private Long chatId;
    private Integer messageId;
    private String messageText;
    private InlineKeyboardMarkup keyboard;
    private String parseMode = "HTML";
    private boolean isEditMessage = false;

    public TelegramMessageBuilder(TelegramMessageFactory messageFactory) {
        this.messageFactory = messageFactory;
    }

    public TelegramMessageBuilder chatId(Long chatId) {
        this.chatId = chatId;
        return this;
    }

    public TelegramMessageBuilder messageId(Integer messageId) {
        this.messageId = messageId;
        this.isEditMessage = true;
        return this;
    }

    public TelegramMessageBuilder text(String text) {
        this.messageText = text;
        return this;
    }

    public TelegramMessageBuilder formattedContent(String contentType, Object data) {
        if (messageFactory.supports(contentType)) {
            this.messageText = messageFactory.formatContent(contentType, data);
        } else {
            this.messageText = "❌ Неподдерживаемый тип контента: " + contentType;
        }
        return this;
    }

    public TelegramMessageBuilder keyboard(InlineKeyboardMarkup keyboard) {
        this.keyboard = keyboard;
        return this;
    }

    public TelegramMessageBuilder parseMode(String parseMode) {
        this.parseMode = parseMode;
        return this;
    }

    public TelegramUIResponse build() {
        return TelegramUIResponse.newMessage(chatId, messageText)
                .keyboard(keyboard)
                .parseMode(parseMode)
                .build();
    }
}