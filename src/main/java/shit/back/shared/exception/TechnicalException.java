package shit.back.shared.exception;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Техническое исключение для системных ошибок
 * Заменяет избыточную иерархию технических исключений
 * Следует принципам KISS и YAGNI
 */
public class TechnicalException extends RuntimeException {

    private final String systemError;
    private final String correlationId;
    private final LocalDateTime timestamp;
    private final TechnicalErrorType errorType;

    /**
     * Основной конструктор
     */
    public TechnicalException(String systemError, TechnicalErrorType errorType, Throwable cause) {
        super(systemError, cause);
        this.systemError = systemError;
        this.errorType = errorType;
        this.correlationId = generateCorrelationId();
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Упрощенный конструктор
     */
    public TechnicalException(String systemError, TechnicalErrorType errorType) {
        this(systemError, errorType, null);
    }

    /**
     * Конструктор с причиной
     */
    public TechnicalException(String systemError, Throwable cause) {
        this(systemError, TechnicalErrorType.SYSTEM, cause);
    }

    // Статические методы для создания распространенных типов технических исключений

    /**
     * Создание исключения базы данных
     */
    public static TechnicalException database(String message, Throwable cause) {
        return new TechnicalException("Database error: " + message, TechnicalErrorType.DATABASE, cause);
    }

    /**
     * Создание исключения кэша
     */
    public static TechnicalException cache(String message, Throwable cause) {
        return new TechnicalException("Cache error: " + message, TechnicalErrorType.CACHE, cause);
    }

    /**
     * Создание исключения внешнего API
     */
    public static TechnicalException externalApi(String message, Throwable cause) {
        return new TechnicalException("External API error: " + message, TechnicalErrorType.EXTERNAL_API, cause);
    }

    /**
     * Создание системного исключения
     */
    public static TechnicalException system(String message, Throwable cause) {
        return new TechnicalException("System error: " + message, TechnicalErrorType.SYSTEM, cause);
    }

    /**
     * Создание исключения конфигурации
     */
    public static TechnicalException configuration(String message) {
        return new TechnicalException("Configuration error: " + message, TechnicalErrorType.CONFIGURATION);
    }

    // Геттеры
    public String getSystemError() {
        return systemError;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public TechnicalErrorType getErrorType() {
        return errorType;
    }

    /**
     * Проверить критичность технической ошибки
     */
    public boolean isCritical() {
        return errorType == TechnicalErrorType.DATABASE ||
                errorType == TechnicalErrorType.SYSTEM;
    }

    /**
     * Получить уровень серьезности для мониторинга
     */
    public String getSeverityLevel() {
        return switch (errorType) {
            case DATABASE, SYSTEM -> "CRITICAL";
            case CACHE, EXTERNAL_API -> "HIGH";
            case CONFIGURATION -> "MEDIUM";
        };
    }

    /**
     * Генерация correlation ID для трассировки
     */
    private String generateCorrelationId() {
        return "TECH_" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    /**
     * Типы технических ошибок
     */
    public enum TechnicalErrorType {
        DATABASE("База данных"),
        CACHE("Кэш"),
        EXTERNAL_API("Внешний API"),
        SYSTEM("Система"),
        CONFIGURATION("Конфигурация");

        private final String displayName;

        TechnicalErrorType(String displayName) {
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
        return String.format("%s[correlationId=%s, errorType=%s, timestamp=%s]: %s",
                this.getClass().getSimpleName(),
                correlationId,
                errorType,
                timestamp,
                systemError);
    }
}