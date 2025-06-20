package shit.back.exception.core;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Базовое исключение для всех бизнес-логических ошибок
 * Предоставляет единую структуру для обработки исключений
 * 
 * @author TelegramStarManager
 * @since Week 3-4 Refactoring
 */
public abstract class BaseBusinessException extends RuntimeException {

    private final String errorCode;
    private final String userFriendlyMessage;
    private final String correlationId;
    private final LocalDateTime timestamp;
    private final Map<String, Object> context;
    private final ErrorSeverity severity;

    /**
     * Конструктор с полным набором параметров
     */
    protected BaseBusinessException(String errorCode,
            String technicalMessage,
            String userFriendlyMessage,
            ErrorSeverity severity,
            Map<String, Object> context,
            Throwable cause) {
        super(technicalMessage, cause);
        this.errorCode = errorCode;
        this.userFriendlyMessage = userFriendlyMessage;
        this.severity = severity;
        this.context = context;
        this.correlationId = generateCorrelationId();
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Упрощенный конструктор для стандартных случаев
     */
    protected BaseBusinessException(String errorCode,
            String technicalMessage,
            String userFriendlyMessage,
            ErrorSeverity severity) {
        this(errorCode, technicalMessage, userFriendlyMessage, severity, Map.of(), null);
    }

    /**
     * Конструктор с причиной
     */
    protected BaseBusinessException(String errorCode,
            String technicalMessage,
            String userFriendlyMessage,
            ErrorSeverity severity,
            Throwable cause) {
        this(errorCode, technicalMessage, userFriendlyMessage, severity, Map.of(), cause);
    }

    // Геттеры
    public String getErrorCode() {
        return errorCode;
    }

    public String getUserFriendlyMessage() {
        return userFriendlyMessage;
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