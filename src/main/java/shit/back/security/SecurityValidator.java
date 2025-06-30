package shit.back.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import shit.back.config.SecurityConstants;
import shit.back.config.SecurityProperties;

import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
public class SecurityValidator {

    @Autowired
    private SecurityProperties securityProperties;

    // Паттерны для валидации
    private static final Pattern SAFE_TEXT_PATTERN = Pattern
            .compile("^[a-zA-Z0-9а-яА-Я\\s.,!?@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?`~]*$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,32}$");
    private static final Pattern PACKAGE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,10}$");
    private static final Pattern ORDER_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-]{8,36}$");

    // Опасные паттерны
    private static final String[] DANGEROUS_PATTERNS = {
            "<script", "</script>", "javascript:", "vbscript:", "onload=", "onerror=",
            "eval(", "setTimeout(", "setInterval(", "Function(", "constructor",
            "SELECT ", "INSERT ", "UPDATE ", "DELETE ", "DROP ", "CREATE ",
            "UNION ", "OR 1=1", "' OR '", "\" OR \"", "--", "/*", "*/",
            "../", "..\\", "/etc/", "\\windows\\", "cmd.exe", "powershell"
    };

    /**
     * Валидация callback данных от Telegram
     */
    public ValidationResult validateCallbackData(String callbackData) {
        if (!securityProperties.getValidation().isEnabled()) {
            return ValidationResult.success();
        }

        if (!StringUtils.hasText(callbackData)) {
            return ValidationResult.error("Callback data is empty");
        }

        // Проверка длины
        if (callbackData.length() > securityProperties.getValidation().getMaxCallbackDataLength()) {
            log.warn("Callback data too long: {} characters", callbackData.length());
            return ValidationResult.error("Callback data too long");
        }

        // Проверка на опасные паттерны
        String lowerData = callbackData.toLowerCase();
        for (String pattern : DANGEROUS_PATTERNS) {
            if (lowerData.contains(pattern.toLowerCase())) {
                log.warn("Dangerous pattern detected in callback data: {}", pattern);
                return ValidationResult.error("Invalid callback data");
            }
        }

        // Проверка разрешенных префиксов
        List<String> allowedPrefixes = securityProperties.getValidation().getAllowedCallbackPrefixes();
        log.debug("🔍 ОТЛАДКА SecurityValidator: allowedPrefixes={}, size={}", allowedPrefixes,
                allowedPrefixes != null ? allowedPrefixes.size() : "null");

        if (allowedPrefixes != null && !allowedPrefixes.isEmpty()) {
            boolean isAllowed = allowedPrefixes.stream()
                    .anyMatch(prefix -> callbackData.startsWith(prefix));

            log.debug("🔍 ОТЛАДКА SecurityValidator: callbackData='{}', isAllowed={}", callbackData, isAllowed);

            if (!isAllowed) {
                log.warn("Callback data with unauthorized prefix: {} (allowed: {})", callbackData, allowedPrefixes);
                return ValidationResult.error("Unauthorized callback prefix");
            }
        }

        return ValidationResult.success();
    }

    /**
     * Валидация текстовых сообщений пользователей
     */
    public ValidationResult validateUserMessage(String message) {
        if (!securityProperties.getValidation().isEnabled()) {
            return ValidationResult.success();
        }

        if (!StringUtils.hasText(message)) {
            return ValidationResult.success(); // Пустые сообщения разрешены
        }

        // Проверка длины
        if (message.length() > securityProperties.getValidation().getMaxMessageLength()) {
            log.warn("User message too long: {} characters", message.length());
            return ValidationResult.error("Message too long");
        }

        // Проверка на опасные паттерны
        String lowerMessage = message.toLowerCase();
        for (String pattern : DANGEROUS_PATTERNS) {
            if (lowerMessage.contains(pattern.toLowerCase())) {
                log.warn("Dangerous pattern detected in user message: {}", pattern);
                return ValidationResult.error("Invalid message content");
            }
        }

        return ValidationResult.success();
    }

    /**
     * Валидация имени пользователя
     */
    public ValidationResult validateUsername(String username) {
        if (!securityProperties.getValidation().isEnabled()) {
            return ValidationResult.success();
        }

        if (!StringUtils.hasText(username)) {
            return ValidationResult.success(); // Username может быть пустым
        }

        if (username.length() > SecurityConstants.MAX_USERNAME_LENGTH) {
            return ValidationResult.error("Username too long");
        }

        if (!USERNAME_PATTERN.matcher(username).matches()) {
            log.warn("Invalid username format: {}", username);
            return ValidationResult.error("Invalid username format");
        }

        return ValidationResult.success();
    }

    /**
     * Валидация ID пакета
     */
    public ValidationResult validatePackageId(String packageId) {
        if (!securityProperties.getValidation().isEnabled()) {
            return ValidationResult.success();
        }

        if (!StringUtils.hasText(packageId)) {
            return ValidationResult.error("Package ID is required");
        }

        if (packageId.length() < SecurityConstants.MIN_PACKAGE_ID_LENGTH ||
                packageId.length() > SecurityConstants.MAX_PACKAGE_ID_LENGTH) {
            return ValidationResult.error("Invalid package ID length");
        }

        if (!PACKAGE_ID_PATTERN.matcher(packageId).matches()) {
            log.warn("Invalid package ID format: {}", packageId);
            return ValidationResult.error("Invalid package ID format");
        }

        return ValidationResult.success();
    }

    /**
     * Валидация ID заказа
     */
    public ValidationResult validateOrderId(String orderId) {
        if (!securityProperties.getValidation().isEnabled()) {
            return ValidationResult.success();
        }

        if (!StringUtils.hasText(orderId)) {
            return ValidationResult.error("Order ID is required");
        }

        if (!ORDER_ID_PATTERN.matcher(orderId).matches()) {
            log.warn("Invalid order ID format: {}", orderId);
            return ValidationResult.error("Invalid order ID format");
        }

        return ValidationResult.success();
    }

    /**
     * Санитизация текста для безопасного отображения
     */
    public String sanitizeText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }

        // Удаляем потенциально опасные символы
        String sanitized = text
                .replaceAll("<", "&lt;")
                .replaceAll(">", "&gt;")
                .replaceAll("\"", "&quot;")
                .replaceAll("'", "&#x27;")
                .replaceAll("&", "&amp;")
                .replaceAll("/", "&#x2F;");

        // Ограничиваем длину
        if (sanitized.length() > SecurityConstants.MAX_MESSAGE_LENGTH) {
            sanitized = sanitized.substring(0, SecurityConstants.MAX_MESSAGE_LENGTH) + "...";
        }

        return sanitized;
    }

    /**
     * Результат валидации
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult error(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
