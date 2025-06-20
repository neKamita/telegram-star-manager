package shit.back.exception.core;

import java.util.Map;

/**
 * Исключение для ошибок безопасности и авторизации
 * Используется для всех security-related ошибок в системе
 * 
 * @author TelegramStarManager
 * @since Week 3-4 Refactoring
 */
public class SecurityException extends BaseBusinessException {

    private final SecurityViolationType violationType;
    private final String resourceId;
    private final String userId;
    private final String action;

    /**
     * Конструктор с полным набором параметров
     */
    public SecurityException(SecurityViolationType violationType,
            String resourceId,
            String userId,
            String action,
            String technicalMessage,
            Map<String, Object> context,
            Throwable cause) {
        super(
                generateErrorCode(violationType),
                technicalMessage,
                generateUserMessage(violationType, action),
                ErrorSeverity.HIGH,
                enrichContext(context, resourceId, userId, action),
                cause);
        this.violationType = violationType;
        this.resourceId = resourceId;
        this.userId = userId;
        this.action = action;
    }

    /**
     * Упрощенный конструктор для стандартных случаев
     */
    public SecurityException(SecurityViolationType violationType,
            String resourceId,
            String userId,
            String action,
            String technicalMessage) {
        this(violationType, resourceId, userId, action, technicalMessage, Map.of(), null);
    }

    /**
     * Конструктор для авторизационных ошибок
     */
    public static SecurityException unauthorized(String userId, String action, String resource) {
        return new SecurityException(
                SecurityViolationType.UNAUTHORIZED_ACCESS,
                resource,
                userId,
                action,
                String.format("User %s attempted unauthorized access to %s for action %s", userId, resource, action));
    }

    /**
     * Конструктор для ошибок недостаточных прав
     */
    public static SecurityException insufficientPermissions(String userId, String action, String resource) {
        return new SecurityException(
                SecurityViolationType.INSUFFICIENT_PERMISSIONS,
                resource,
                userId,
                action,
                String.format("User %s has insufficient permissions for action %s on resource %s", userId, action,
                        resource));
    }

    /**
     * Конструктор для ошибок валидации токенов
     */
    public static SecurityException invalidToken(String userId, String tokenType) {
        return new SecurityException(
                SecurityViolationType.INVALID_TOKEN,
                tokenType,
                userId,
                "TOKEN_VALIDATION",
                String.format("Invalid or expired %s token for user %s", tokenType, userId));
    }

    /**
     * Конструктор для подозрительной активности
     */
    public static SecurityException suspiciousActivity(String userId, String activity, String details) {
        return new SecurityException(
                SecurityViolationType.SUSPICIOUS_ACTIVITY,
                activity,
                userId,
                "SECURITY_MONITORING",
                String.format("Suspicious activity detected for user %s: %s - %s", userId, activity, details));
    }

    /**
     * Конструктор для превышения лимитов безопасности
     */
    public static SecurityException securityLimitExceeded(String userId, String limitType, String details) {
        return new SecurityException(
                SecurityViolationType.SECURITY_LIMIT_EXCEEDED,
                limitType,
                userId,
                "LIMIT_ENFORCEMENT",
                String.format("Security limit exceeded for user %s: %s - %s", userId, limitType, details));
    }

    // Геттеры
    public SecurityViolationType getViolationType() {
        return violationType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getUserId() {
        return userId;
    }

    public String getAction() {
        return action;
    }

    /**
     * Генерация кода ошибки на основе типа нарушения
     */
    private static String generateErrorCode(SecurityViolationType violationType) {
        return "SEC_" + violationType.getCode();
    }

    /**
     * Генерация пользовательского сообщения
     */
    private static String generateUserMessage(SecurityViolationType violationType, String action) {
        return switch (violationType) {
            case UNAUTHORIZED_ACCESS -> "Доступ запрещен. Необходима авторизация.";
            case INSUFFICIENT_PERMISSIONS -> "Недостаточно прав для выполнения операции.";
            case INVALID_TOKEN -> "Недействительный или истекший токен авторизации.";
            case SUSPICIOUS_ACTIVITY -> "Обнаружена подозрительная активность. Доступ временно ограничен.";
            case SECURITY_LIMIT_EXCEEDED -> "Превышены лимиты безопасности. Попробуйте позже.";
            case ADMIN_ACCESS_DENIED -> "Доступ к административным функциям запрещен.";
            case RATE_LIMIT_EXCEEDED -> "Слишком много запросов. Попробуйте позже.";
        };
    }

    /**
     * Обогащение контекста дополнительными данными безопасности
     */
    private static Map<String, Object> enrichContext(Map<String, Object> originalContext,
            String resourceId,
            String userId,
            String action) {
        var enrichedContext = new java.util.HashMap<>(originalContext);
        enrichedContext.put("securityEvent", true);
        enrichedContext.put("resourceId", resourceId);
        enrichedContext.put("userId", userId);
        enrichedContext.put("action", action);
        enrichedContext.put("timestamp", java.time.LocalDateTime.now().toString());
        return enrichedContext;
    }

    /**
     * Типы нарушений безопасности
     */
    public enum SecurityViolationType {
        UNAUTHORIZED_ACCESS("001", "Неавторизованный доступ"),
        INSUFFICIENT_PERMISSIONS("002", "Недостаточно прав"),
        INVALID_TOKEN("003", "Недействительный токен"),
        SUSPICIOUS_ACTIVITY("004", "Подозрительная активность"),
        SECURITY_LIMIT_EXCEEDED("005", "Превышение лимитов безопасности"),
        ADMIN_ACCESS_DENIED("006", "Отказ в административном доступе"),
        RATE_LIMIT_EXCEEDED("007", "Превышение лимита запросов");

        private final String code;
        private final String description;

        SecurityViolationType(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }
    }
}