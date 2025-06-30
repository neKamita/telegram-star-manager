package shit.back.telegram.ui;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

/**
 * Унифицированный ответ UI компонентов Telegram
 * 
 * Перенесен из presentation layer для использования в новой архитектуре
 */
public class TelegramUIResponse {

    private final String messageText;
    private final InlineKeyboardMarkup keyboard;
    private final String parseMode;
    private final boolean isEditMessage;
    private final Long chatId;
    private final Integer messageId;

    private TelegramUIResponse(Builder builder) {
        this.messageText = builder.messageText;
        this.keyboard = builder.keyboard;
        this.parseMode = builder.parseMode;
        this.isEditMessage = builder.isEditMessage;
        this.chatId = builder.chatId;
        this.messageId = builder.messageId;
    }

    // Getters
    public String getMessageText() {
        return messageText;
    }

    public InlineKeyboardMarkup getKeyboard() {
        return keyboard;
    }

    public String getParseMode() {
        return parseMode;
    }

    public boolean isEditMessage() {
        return isEditMessage;
    }

    public Long getChatId() {
        return chatId;
    }

    public Integer getMessageId() {
        return messageId;
    }

    public boolean hasKeyboard() {
        return keyboard != null;
    }

    // Static factory methods
    public static Builder newMessage(Long chatId, String text) {
        return new Builder().chatId(chatId).messageText(text);
    }

    public static Builder editMessage(Long chatId, Integer messageId, String text) {
        return new Builder().chatId(chatId).messageId(messageId).messageText(text).isEditMessage(true);
    }

    // Builder pattern
    public static class Builder {
        private String messageText;
        private InlineKeyboardMarkup keyboard;
        private String parseMode = "HTML";
        private boolean isEditMessage = false;
        private Long chatId;
        private Integer messageId;

        public Builder messageText(String messageText) {
            this.messageText = messageText;
            return this;
        }

        public Builder keyboard(InlineKeyboardMarkup keyboard) {
            this.keyboard = keyboard;
            return this;
        }

        public Builder parseMode(String parseMode) {
            this.parseMode = parseMode;
            return this;
        }

        public Builder isEditMessage(boolean isEditMessage) {
            this.isEditMessage = isEditMessage;
            return this;
        }

        public Builder chatId(Long chatId) {
            this.chatId = chatId;
            return this;
        }

        public Builder messageId(Integer messageId) {
            this.messageId = messageId;
            return this;
        }

        public TelegramUIResponse build() {
            if (chatId == null) {
                throw new IllegalStateException("chatId обязателен");
            }
            if (messageText == null || messageText.trim().isEmpty()) {
                throw new IllegalStateException("messageText обязателен");
            }
            if (isEditMessage && messageId == null) {
                throw new IllegalStateException("messageId обязателен для редактирования");
            }

            return new TelegramUIResponse(this);
        }
    }

    @Override
    public String toString() {
        return String.format("TelegramUIResponse{chatId=%d, isEdit=%s, hasKeyboard=%s}",
                chatId, isEditMessage, hasKeyboard());
    }
}