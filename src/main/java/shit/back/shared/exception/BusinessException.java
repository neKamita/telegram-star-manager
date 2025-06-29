package shit.back.shared.exception;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Упрощенное бизнес-исключение для всех бизнес-логических ошибок
 * Заменяет BaseBusinessException и объединяет SecurityException,
 * ValidationException
 * Следует принципам KISS и YAGNI - только необходимая функциональность
 */
public class BusinessException extends RuntimeException {

    private final String errorCode;
    private final String userMessage;
    private final String correlationId;
    private final LocalDateTime timestamp;
    private final Map<String, Object> context;
    private final ErrorSeverity severity;

    /**
     * Основной конструктор
     */
    public BusinessException(String errorCode,
            String technicalMessage,
            String userMessage,
            ErrorSeverity severity,
            Map<String, Object> context,
            Throwable cause) {
        super(technicalMessage, cause);
        this.errorCode = errorCode;
        this.userMessage = userMessage;
        this.severity = severity;
        this.context = context != null ? context : Map.of();
        this.correlationId = generateCorrelationId();
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Упрощенный конструктор
     */
    public BusinessException(String errorCode, String technicalMessage, String userMessage) {
        this(errorCode, technicalMessage, userMessage, ErrorSeverity.MEDIUM, Map.of(), null);
    }

    /**
     * Конструктор с причиной
     */
    public BusinessException(String errorCode, String technicalMessage, String userMessage, Throwable cause) {
        this(errorCode, technicalMessage, userMessage, ErrorSeverity.MEDIUM, Map.of(), cause);
    }

    /**
     * Конструктор с критичностью
     */
    public BusinessException(String errorCode, String technicalMessage, String userMessage, ErrorSeverity severity) {
        this(errorCode, technicalMessage, userMessage, severity, Map.of(), null);
    }

    // Статические методы для создания распространенных типов исключений

    /**
     * Создание исключения валидации
     */
    public static BusinessException validation(String message, String userMessage) {
        return new BusinessException("VALIDATION_ERROR", message, userMessage, ErrorSeverity.LOW);
    }

    /**
     * Создание исключения безопасности
     */
    public static BusinessException security(String message, String userMessage) {
        return new BusinessException("SECURITY_ERROR", message, userMessage, ErrorSeverity.HIGH);
    }

    /**
     * Создание исключения заказа
     */
    public static BusinessException order(String message, String userMessage) {
        return new BusinessException("ORDER_ERROR", message, userMessage, ErrorSeverity.MEDIUM);
    }

    /**
     * Создание исключения баланса
     */
    public static BusinessException balance(String message, String userMessage) {
        return new BusinessException("BALANCE_ERROR", message, userMessage, ErrorSeverity.MEDIUM);
    }

    /**
     * Создание исключения пользователя
     */
    public static BusinessException user(String message, String userMessage) {
        return new BusinessException("USER_ERROR", message, userMessage, ErrorSeverity.LOW);
    }

    // Геттеры
    public String getErrorCode() {
        return errorCode;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public ErrorSeverity getSeverity() {
        return severity;
    }

    /**
     * Получить значение из контекста
     */
    public Object getContextValue(String key) {
        return context.get(key);
    }

    /**
     * Проверить критичность ошибки
     */
    public boolean isCritical() {
        return severity == ErrorSeverity.CRITICAL;
    }

    /**
     * Проверить требует ли ошибка немедленного внимания
     */
    public boolean requiresImmediateAttention() {
        return severity == ErrorSeverity.CRITICAL || severity == ErrorSeverity.HIGH;
    }

    /**
     * Генерация correlation ID для трассировки
     */
    private String generateCorrelationId() {
        return "CORR_" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    /**
     * Уровни критичности ошибок
     */
    public enum ErrorSeverity {
        LOW("Низкая"),
        MEDIUM("Средняя"),
        HIGH("Высокая"),
        CRITICAL("Критическая");

        private final String displayName;

        ErrorSeverity(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Переопределение toString для лучшего логирования
     */
    @Override
    public String toString() {
        return String.format("%s[errorCode=%s, correlationId=%s, severity=%s, timestamp=%s]: %s",
                this.getClass().getSimpleName(),
                errorCode,
                correlationId,
                severity,
                timestamp,
                getMessage());
    }
}