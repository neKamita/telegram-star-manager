package shit.back.telegram.dto;

/**
 * Унифицированный DTO для всех ответов Telegram системы
 * 
 * Объединяет функциональность старых TelegramResponse и TelegramUIResponse
 */
public class TelegramResponse {

    private final boolean successful;
    private final String message;
    private final String errorMessage;
    private final Object data;
    private final String uiType;
    private final Object uiData;
    private final String parseMode;

    private TelegramResponse(Builder builder) {
        this.successful = builder.successful;
        this.message = builder.message;
        this.errorMessage = builder.errorMessage;
        this.data = builder.data;
        this.uiType = builder.uiType;
        this.uiData = builder.uiData;
        this.parseMode = builder.parseMode != null ? builder.parseMode : "HTML";
    }

    // Основные методы
    public boolean isSuccessful() {
        return successful;
    }

    public String getMessage() {
        return message;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Object getData() {
        return data;
    }

    public String getUiType() {
        return uiType;
    }

    public Object getUiData() {
        return uiData;
    }

    public String getParseMode() {
        return parseMode;
    }

    public boolean hasUI() {
        return uiType != null;
    }

    public boolean hasData() {
        return data != null;
    }

    /**
     * Получить клавиатуру из данных ответа
     * Клавиатура может храниться в поле data
     */
    public org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup getKeyboard() {
        if (data instanceof org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup) {
            return (org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup) data;
        }
        return null;
    }

    /**
     * Проверить наличие клавиатуры
     */
    public boolean hasKeyboard() {
        return getKeyboard() != null;
    }

    @SuppressWarnings("unchecked")
    public <T> T getData(Class<T> type) {
        if (data != null && type.isInstance(data)) {
            return (T) data;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public <T> T getUiData(Class<T> type) {
        if (uiData != null && type.isInstance(uiData)) {
            return (T) data;
        }
        return null;
    }

    // Static factory methods
    public static TelegramResponse success(String message) {
        return builder().successful(true).message(message).build();
    }

    public static TelegramResponse success(String message, Object data) {
        return builder().successful(true).message(message).data(data).build();
    }

    public static TelegramResponse successWithUI(String message, String uiType, Object uiData) {
        return builder()
                .successful(true)
                .message(message)
                .uiType(uiType)
                .uiData(uiData)
                .build();
    }

    public static TelegramResponse error(String errorMessage) {
        return builder().successful(false).errorMessage(errorMessage).build();
    }

    public static TelegramResponse error(String errorMessage, Object data) {
        return builder().successful(false).errorMessage(errorMessage).data(data).build();
    }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean successful = true;
        private String message;
        private String errorMessage;
        private Object data;
        private String uiType;
        private Object uiData;
        private String parseMode;

        public Builder successful(boolean successful) {
            this.successful = successful;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            this.successful = false;
            return this;
        }

        public Builder data(Object data) {
            this.data = data;
            return this;
        }

        public Builder uiType(String uiType) {
            this.uiType = uiType;
            return this;
        }

        public Builder uiData(Object uiData) {
            this.uiData = uiData;
            return this;
        }

        public Builder parseMode(String parseMode) {
            this.parseMode = parseMode;
            return this;
        }

        public Builder withUI(String uiType, Object uiData) {
            this.uiType = uiType;
            this.uiData = uiData;
            return this;
        }

        public TelegramResponse build() {
            return new TelegramResponse(this);
        }
    }

    @Override
    public String toString() {
        return String.format("TelegramResponse{successful=%s, hasMessage=%s, hasError=%s, hasData=%s, hasUI=%s}",
                successful, message != null, errorMessage != null, data != null, uiType != null);
    }
}