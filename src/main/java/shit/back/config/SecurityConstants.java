package shit.back.config;

/**
 * Константы безопасности для Telegram Star Manager
 */
public final class SecurityConstants {
    
    // API Security
    public static final String API_KEY_HEADER = "X-API-KEY";
    public static final String API_PATH_PATTERN = "/api/**";
    public static final String HEALTH_PATH = "/api/bot/health";
    
    // Rate Limiting
    public static final String RATE_LIMIT_USER_PREFIX = "rate_limit:user:";
    public static final String RATE_LIMIT_API_PREFIX = "rate_limit:api:";
    public static final int DEFAULT_USER_LIMIT = 10; // requests per minute
    public static final int DEFAULT_API_LIMIT = 100; // requests per minute
    
    // Security Headers
    public static final String USER_ID_HEADER = "X-User-ID";
    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String CLIENT_IP_HEADER = "X-Client-IP";
    
    // Validation
    public static final int MAX_MESSAGE_LENGTH = 4096;
    public static final int MAX_CALLBACK_DATA_LENGTH = 64;
    public static final int MAX_USERNAME_LENGTH = 32;
    public static final int MIN_PACKAGE_ID_LENGTH = 3;
    public static final int MAX_PACKAGE_ID_LENGTH = 10;
    
    // Security Events
    public static final String EVENT_INVALID_API_KEY = "INVALID_API_KEY";
    public static final String EVENT_RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED";
    public static final String EVENT_INVALID_CALLBACK_DATA = "INVALID_CALLBACK_DATA";
    public static final String EVENT_SUSPICIOUS_ACTIVITY = "SUSPICIOUS_ACTIVITY";
    public static final String EVENT_API_ACCESS = "API_ACCESS";
    
    // Error Messages
    public static final String ERROR_INVALID_API_KEY = "Invalid or missing API key";
    public static final String ERROR_RATE_LIMIT_EXCEEDED = "Rate limit exceeded. Please try again later";
    public static final String ERROR_INVALID_REQUEST = "Invalid request data";
    public static final String ERROR_ACCESS_DENIED = "Access denied";
    
    // Redis Keys
    public static final String REDIS_SESSION_PREFIX = "telegram:session:";
    public static final String REDIS_SECURITY_PREFIX = "telegram:security:";
    public static final String REDIS_AUDIT_PREFIX = "telegram:audit:";
    
    // Timeouts (в секундах)
    public static final long SESSION_TIMEOUT = 3600; // 1 hour
    public static final long RATE_LIMIT_WINDOW = 60; // 1 minute
    public static final long SECURITY_EVENT_TTL = 86400; // 24 hours
    
    private SecurityConstants() {
        // Utility class - no instantiation
    }
}
