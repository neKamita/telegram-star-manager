package shit.back.telegram.exceptions;

/**
 * Базовое исключение для всех ошибок Telegram системы
 * 
 * Заменяет старый TelegramApplicationException
 */
public class TelegramException extends RuntimeException {

    private final String errorCode;
    private final Object context;

    public TelegramException(String message) {
        super(message);
        this.errorCode = "TELEGRAM_ERROR";
        this.context = null;
    }

    public TelegramException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "TELEGRAM_ERROR";
        this.context = null;
    }

    public TelegramException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.context = null;
    }

    public TelegramException(String errorCode, String message, Object context) {
        super(message);
        this.errorCode = errorCode;
        this.context = context;
    }

    public TelegramException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.context = null;
    }

    public TelegramException(String errorCode, String message, Object context, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.context = context;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Object getContext() {
        return context;
    }

    public boolean hasContext() {
        return context != null;
    }

    @Override
    public String toString() {
        return String.format("TelegramException{errorCode='%s', message='%s', hasContext=%s}",
                errorCode, getMessage(), hasContext());
    }

    // Статические методы для создания типичных исключений
    public static TelegramException commandNotFound(String commandType) {
        return new TelegramException("COMMAND_NOT_FOUND",
                "Обработчик команды не найден: " + commandType, commandType);
    }

    public static TelegramException queryNotFound(String queryType) {
        return new TelegramException("QUERY_NOT_FOUND",
                "Обработчик запроса не найден: " + queryType, queryType);
    }

    public static TelegramException validationError(String message) {
        return new TelegramException("VALIDATION_ERROR", message);
    }

    public static TelegramException processingError(String message, Throwable cause) {
        return new TelegramException("PROCESSING_ERROR", message, cause);
    }

    public static TelegramException configurationError(String message) {
        return new TelegramException("CONFIGURATION_ERROR", message);
    }
}