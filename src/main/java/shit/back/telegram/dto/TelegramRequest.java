package shit.back.telegram.dto;

/**
 * Базовый DTO для всех входящих Telegram запросов
 * 
 * Унифицирует обработку различных типов запросов от Telegram API
 */
public class TelegramRequest {

    private final Long userId;
    private final Long chatId;
    private final String requestType;
    private final Object data;
    private final boolean isCommand;

    private TelegramRequest(Builder builder) {
        this.userId = builder.userId;
        this.chatId = builder.chatId;
        this.requestType = builder.requestType;
        this.data = builder.data;
        this.isCommand = builder.isCommand;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getChatId() {
        return chatId;
    }

    public String getRequestType() {
        return requestType;
    }

    public Object getData() {
        return data;
    }

    public boolean isCommand() {
        return isCommand;
    }

    public boolean isQuery() {
        return !isCommand;
    }

    @SuppressWarnings("unchecked")
    public <T> T getData(Class<T> type) {
        if (data != null && type.isInstance(data)) {
            return (T) data;
        }
        return null;
    }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long userId;
        private Long chatId;
        private String requestType;
        private Object data;
        private boolean isCommand = false;

        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder chatId(Long chatId) {
            this.chatId = chatId;
            return this;
        }

        public Builder requestType(String requestType) {
            this.requestType = requestType;
            return this;
        }

        public Builder data(Object data) {
            this.data = data;
            return this;
        }

        public Builder asCommand() {
            this.isCommand = true;
            return this;
        }

        public Builder asQuery() {
            this.isCommand = false;
            return this;
        }

        public TelegramRequest build() {
            if (userId == null || userId <= 0) {
                throw new IllegalArgumentException("User ID обязателен и должен быть положительным");
            }
            if (chatId == null) {
                throw new IllegalArgumentException("Chat ID обязателен");
            }
            if (requestType == null || requestType.trim().isEmpty()) {
                throw new IllegalArgumentException("Request type обязателен");
            }
            return new TelegramRequest(this);
        }
    }

    @Override
    public String toString() {
        return String.format("TelegramRequest{userId=%d, chatId=%d, type='%s', isCommand=%s}",
                userId, chatId, requestType, isCommand);
    }
}