package shit.back.exception.unified;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Контекст исключения для централизованного логирования и трассировки
 * Содержит всю необходимую информацию для debugging и monitoring
 * 
 * @author TelegramStarManager - Stage 3 Refactoring
 * @since Exception Unification Architecture
 */
public class ExceptionContext {

    /** Уникальный идентификатор для трассировки */
    private final String traceId;

    /** Временная метка возникновения исключения */
    private final LocalDateTime timestamp;

    /** HTTP метод запроса */
    private String httpMethod;

    /** Путь запроса */
    private String requestPath;

    /** IP адрес клиента */
    private String clientIp;

    /** User-Agent клиента */
    private String userAgent;

    /** ID пользователя (если авторизован) */
    private String userId;

    /** ID сессии */
    private String sessionId;

    /** Дополнительные контекстные данные */
    private final Map<String, Object> additionalData;

    /** Информация о стеке вызовов */
    private String stackTrace;

    /** Категория исключения */
    private ExceptionCategory category;

    /** Уровень критичности */
    private SeverityLevel severity;

    /** Флаг требования немедленного внимания */
    private boolean requiresImmediateAttention;

    public ExceptionContext() {
        this.traceId = generateTraceId();
        this.timestamp = LocalDateTime.now();
        this.additionalData = new HashMap<>();
        this.severity = SeverityLevel.MEDIUM;
        this.requiresImmediateAttention = false;
    }

    public ExceptionContext(ExceptionCategory category, SeverityLevel severity) {
        this();
        this.category = category;
        this.severity = severity;
        this.requiresImmediateAttention = severity == SeverityLevel.CRITICAL;
    }

    /**
     * Генерация уникального trace ID
     */
    private String generateTraceId() {
        return "TRC-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    /**
     * Добавление дополнительных данных в контекст
     */
    public ExceptionContext addData(String key, Object value) {
        this.additionalData.put(key, value);
        return this;
    }

    /**
     * Добавление нескольких данных одновременно
     */
    public ExceptionContext addAllData(Map<String, Object> data) {
        this.additionalData.putAll(data);
        return this;
    }

    /**
     * Установка HTTP контекста
     */
    public ExceptionContext withHttpContext(String method, String path, String clientIp) {
        this.httpMethod = method;
        this.requestPath = path;
        this.clientIp = clientIp;
        return this;
    }

    /**
     * Установка пользовательского контекста
     */
    public ExceptionContext withUserContext(String userId, String sessionId) {
        this.userId = userId;
        this.sessionId = sessionId;
        return this;
    }

    /**
     * Установка стека вызовов
     */
    public ExceptionContext withStackTrace(Throwable throwable) {
        if (throwable != null) {
            StringBuilder sb = new StringBuilder();
            sb.append(throwable.getClass().getSimpleName()).append(": ").append(throwable.getMessage()).append("\n");
            for (StackTraceElement element : throwable.getStackTrace()) {
                sb.append("\tat ").append(element.toString()).append("\n");
            }
            this.stackTrace = sb.toString();
        }
        return this;
    }

    /**
     * Создание Map для логирования
     */
    public Map<String, Object> toLogMap() {
        Map<String, Object> logData = new HashMap<>();
        logData.put("traceId", traceId);
        logData.put("timestamp", timestamp);
        logData.put("category", category != null ? category.name() : "UNKNOWN");
        logData.put("severity", severity.name());
        logData.put("requiresImmediateAttention", requiresImmediateAttention);

        if (httpMethod != null)
            logData.put("httpMethod", httpMethod);
        if (requestPath != null)
            logData.put("requestPath", requestPath);
        if (clientIp != null)
            logData.put("clientIp", clientIp);
        if (userAgent != null)
            logData.put("userAgent", userAgent);
        if (userId != null)
            logData.put("userId", userId);
        if (sessionId != null)
            logData.put("sessionId", sessionId);

        if (!additionalData.isEmpty()) {
            logData.put("additionalData", additionalData);
        }

        return logData;
    }

    /**
     * Создание краткого описания для логов
     */
    public String toShortString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(traceId).append("] ");
        sb.append(severity.name()).append(" ");
        if (category != null) {
            sb.append(category.name()).append(" ");
        }
        if (requestPath != null) {
            sb.append("@ ").append(requestPath).append(" ");
        }
        if (userId != null) {
            sb.append("user:").append(userId);
        }
        return sb.toString().trim();
    }

    // Геттеры и сеттеры
    public String getTraceId() {
        return traceId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public void setRequestPath(String requestPath) {
        this.requestPath = requestPath;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Map<String, Object> getAdditionalData() {
        return additionalData;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }

    public ExceptionCategory getCategory() {
        return category;
    }

    public void setCategory(ExceptionCategory category) {
        this.category = category;
    }

    public SeverityLevel getSeverity() {
        return severity;
    }

    public void setSeverity(SeverityLevel severity) {
        this.severity = severity;
        this.requiresImmediateAttention = severity == SeverityLevel.CRITICAL;
    }

    public boolean isRequiresImmediateAttention() {
        return requiresImmediateAttention;
    }

    public void setRequiresImmediateAttention(boolean requiresImmediateAttention) {
        this.requiresImmediateAttention = requiresImmediateAttention;
    }

    /**
     * Категории исключений для классификации
     */
    public enum ExceptionCategory {
        BUSINESS_LOGIC("Бизнес-логика"),
        SECURITY("Безопасность"),
        VALIDATION("Валидация"),
        EXTERNAL_SERVICE("Внешний сервис"),
        DATABASE("База данных"),
        NETWORK("Сеть"),
        SYSTEM("Система"),
        UNKNOWN("Неизвестно");

        private final String displayName;

        ExceptionCategory(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Уровни критичности исключений
     */
    public enum SeverityLevel {
        LOW("Низкий"),
        MEDIUM("Средний"),
        HIGH("Высокий"),
        CRITICAL("Критический");

        private final String displayName;

        SeverityLevel(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}