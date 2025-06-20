package shit.back.exception.registry;

import shit.back.exception.unified.ExceptionContext;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Централизованный реестр кодов ошибок
 * Обеспечивает консистентность кодов ошибок по всему приложению
 * 
 * @author TelegramStarManager - Stage 3 Refactoring
 * @since Exception Unification Architecture
 */
public class ErrorCodeRegistry {

    private static final Map<String, ErrorCodeDefinition> ERROR_CODES = new HashMap<>();

    static {
        // Инициализация кодов ошибок при загрузке класса
        initializeErrorCodes();
    }

    /**
     * Инициализация всех кодов ошибок приложения
     */
    private static void initializeErrorCodes() {
        // === БИЗНЕС-ЛОГИЧЕСКИЕ ОШИБКИ ===

        // Ошибки баланса (BAL_xxx)
        register("BAL_001", "Недостаточно средств на балансе",
                "Insufficient balance for operation", HttpStatus.PAYMENT_REQUIRED,
                ExceptionContext.ExceptionCategory.BUSINESS_LOGIC,
                ExceptionContext.SeverityLevel.MEDIUM,
                "Пополните баланс для продолжения операции");

        register("BAL_002", "Баланс пользователя не найден",
                "User balance not found", HttpStatus.NOT_FOUND,
                ExceptionContext.ExceptionCategory.BUSINESS_LOGIC,
                ExceptionContext.SeverityLevel.HIGH,
                "Обратитесь в поддержку для создания баланса");

        register("BAL_003", "Неверная сумма операции",
                "Invalid amount for balance operation", HttpStatus.BAD_REQUEST,
                ExceptionContext.ExceptionCategory.VALIDATION,
                ExceptionContext.SeverityLevel.LOW,
                "Проверьте сумму операции");

        register("BAL_004", "Превышен лимит операций",
                "Transaction limit exceeded", HttpStatus.TOO_MANY_REQUESTS,
                ExceptionContext.ExceptionCategory.BUSINESS_LOGIC,
                ExceptionContext.SeverityLevel.MEDIUM,
                "Попробуйте позже или обратитесь в поддержку");

        register("BAL_005", "Конфликт одновременных операций",
                "Concurrent balance operation conflict", HttpStatus.CONFLICT,
                ExceptionContext.ExceptionCategory.BUSINESS_LOGIC,
                ExceptionContext.SeverityLevel.HIGH,
                "Повторите операцию через несколько секунд");

        // Ошибки транзакций (TXN_xxx)
        register("TXN_001", "Транзакция не найдена",
                "Transaction not found", HttpStatus.NOT_FOUND,
                ExceptionContext.ExceptionCategory.BUSINESS_LOGIC,
                ExceptionContext.SeverityLevel.MEDIUM,
                "Проверьте корректность ID транзакции");

        register("TXN_002", "Неверный статус транзакции",
                "Invalid transaction status", HttpStatus.BAD_REQUEST,
                ExceptionContext.ExceptionCategory.BUSINESS_LOGIC,
                ExceptionContext.SeverityLevel.MEDIUM,
                "Операция недоступна для данного статуса транзакции");

        register("TXN_003", "Транзакция уже обработана",
                "Transaction already processed", HttpStatus.CONFLICT,
                ExceptionContext.ExceptionCategory.BUSINESS_LOGIC,
                ExceptionContext.SeverityLevel.LOW,
                "Транзакция уже была обработана ранее");

        // === ОШИБКИ БЕЗОПАСНОСТИ ===

        // Ошибки авторизации (SEC_xxx)
        register("SEC_001", "Доступ запрещен",
                "Access denied", HttpStatus.FORBIDDEN,
                ExceptionContext.ExceptionCategory.SECURITY,
                ExceptionContext.SeverityLevel.HIGH,
                "Недостаточно прав для выполнения операции");

        register("SEC_002", "Неверный токен авторизации",
                "Invalid authorization token", HttpStatus.UNAUTHORIZED,
                ExceptionContext.ExceptionCategory.SECURITY,
                ExceptionContext.SeverityLevel.HIGH,
                "Войдите в систему заново");

        register("SEC_003", "Подозрительная активность",
                "Suspicious activity detected", HttpStatus.FORBIDDEN,
                ExceptionContext.ExceptionCategory.SECURITY,
                ExceptionContext.SeverityLevel.CRITICAL,
                "Аккаунт временно заблокирован. Обратитесь в поддержку");

        register("SEC_004", "Превышен лимит запросов",
                "Rate limit exceeded", HttpStatus.TOO_MANY_REQUESTS,
                ExceptionContext.ExceptionCategory.SECURITY,
                ExceptionContext.SeverityLevel.MEDIUM,
                "Слишком много запросов. Попробуйте позже");

        register("SEC_005", "Доступ только для администраторов",
                "Admin access required", HttpStatus.FORBIDDEN,
                ExceptionContext.ExceptionCategory.SECURITY,
                ExceptionContext.SeverityLevel.HIGH,
                "Данная функция доступна только администраторам");

        // === ОШИБКИ ВАЛИДАЦИИ ===

        // Ошибки валидации (VAL_xxx)
        register("VAL_001", "Ошибка валидации входных данных",
                "Input validation failed", HttpStatus.BAD_REQUEST,
                ExceptionContext.ExceptionCategory.VALIDATION,
                ExceptionContext.SeverityLevel.LOW,
                "Проверьте правильность заполнения полей");

        register("VAL_002", "Обязательное поле не заполнено",
                "Required field is missing", HttpStatus.BAD_REQUEST,
                ExceptionContext.ExceptionCategory.VALIDATION,
                ExceptionContext.SeverityLevel.LOW,
                "Заполните все обязательные поля");

        register("VAL_003", "Неверный формат данных",
                "Invalid data format", HttpStatus.BAD_REQUEST,
                ExceptionContext.ExceptionCategory.VALIDATION,
                ExceptionContext.SeverityLevel.LOW,
                "Проверьте формат введенных данных");

        // === СИСТЕМНЫЕ ОШИБКИ ===

        // Ошибки внешних сервисов (EXT_xxx)
        register("EXT_001", "Ошибка внешнего сервиса",
                "External service error", HttpStatus.BAD_GATEWAY,
                ExceptionContext.ExceptionCategory.EXTERNAL_SERVICE,
                ExceptionContext.SeverityLevel.HIGH,
                "Временная недоступность сервиса. Попробуйте позже");

        register("EXT_002", "Таймаут внешнего сервиса",
                "External service timeout", HttpStatus.GATEWAY_TIMEOUT,
                ExceptionContext.ExceptionCategory.EXTERNAL_SERVICE,
                ExceptionContext.SeverityLevel.MEDIUM,
                "Сервис не отвечает. Попробуйте позже");

        // Ошибки базы данных (DB_xxx)
        register("DB_001", "Ошибка соединения с базой данных",
                "Database connection error", HttpStatus.INTERNAL_SERVER_ERROR,
                ExceptionContext.ExceptionCategory.DATABASE,
                ExceptionContext.SeverityLevel.CRITICAL,
                "Технические проблемы. Обратитесь в поддержку");

        register("DB_002", "Нарушение целостности данных",
                "Data integrity violation", HttpStatus.CONFLICT,
                ExceptionContext.ExceptionCategory.DATABASE,
                ExceptionContext.SeverityLevel.HIGH,
                "Конфликт данных. Обратитесь в поддержку");

        // Общие системные ошибки (SYS_xxx)
        register("SYS_001", "Внутренняя ошибка сервера",
                "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR,
                ExceptionContext.ExceptionCategory.SYSTEM,
                ExceptionContext.SeverityLevel.CRITICAL,
                "Произошла внутренняя ошибка. Обратитесь в поддержку");

        register("SYS_002", "Сервис временно недоступен",
                "Service temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE,
                ExceptionContext.ExceptionCategory.SYSTEM,
                ExceptionContext.SeverityLevel.HIGH,
                "Сервис временно недоступен. Попробуйте позже");

        register("SYS_003", "Неизвестная ошибка",
                "Unknown error", HttpStatus.INTERNAL_SERVER_ERROR,
                ExceptionContext.ExceptionCategory.UNKNOWN,
                ExceptionContext.SeverityLevel.MEDIUM,
                "Произошла неопознанная ошибка");
    }

    /**
     * Регистрация нового кода ошибки
     */
    private static void register(String code, String userMessage, String technicalMessage,
            HttpStatus httpStatus, ExceptionContext.ExceptionCategory category,
            ExceptionContext.SeverityLevel severity, String actionRequired) {
        ERROR_CODES.put(code, new ErrorCodeDefinition(
                code, userMessage, technicalMessage, httpStatus,
                category, severity, actionRequired));
    }

    /**
     * Получение определения ошибки по коду
     */
    public static Optional<ErrorCodeDefinition> getErrorDefinition(String code) {
        return Optional.ofNullable(ERROR_CODES.get(code));
    }

    /**
     * Проверка существования кода ошибки
     */
    public static boolean exists(String code) {
        return ERROR_CODES.containsKey(code);
    }

    /**
     * Получение HTTP статуса по коду ошибки
     */
    public static HttpStatus getHttpStatus(String code) {
        return getErrorDefinition(code)
                .map(ErrorCodeDefinition::getHttpStatus)
                .orElse(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Получение пользовательского сообщения по коду ошибки
     */
    public static String getUserMessage(String code) {
        return getErrorDefinition(code)
                .map(ErrorCodeDefinition::getUserMessage)
                .orElse("Произошла неизвестная ошибка");
    }

    /**
     * Получение технического сообщения по коду ошибки
     */
    public static String getTechnicalMessage(String code) {
        return getErrorDefinition(code)
                .map(ErrorCodeDefinition::getTechnicalMessage)
                .orElse("Unknown error");
    }

    /**
     * Получение категории исключения по коду ошибки
     */
    public static ExceptionContext.ExceptionCategory getCategory(String code) {
        return getErrorDefinition(code)
                .map(ErrorCodeDefinition::getCategory)
                .orElse(ExceptionContext.ExceptionCategory.UNKNOWN);
    }

    /**
     * Получение уровня критичности по коду ошибки
     */
    public static ExceptionContext.SeverityLevel getSeverity(String code) {
        return getErrorDefinition(code)
                .map(ErrorCodeDefinition::getSeverity)
                .orElse(ExceptionContext.SeverityLevel.MEDIUM);
    }

    /**
     * Получение рекомендуемых действий по коду ошибки
     */
    public static String getActionRequired(String code) {
        return getErrorDefinition(code)
                .map(ErrorCodeDefinition::getActionRequired)
                .orElse("Обратитесь в службу поддержки");
    }

    /**
     * Получение всех зарегистрированных кодов ошибок
     */
    public static Map<String, ErrorCodeDefinition> getAllErrorCodes() {
        return new HashMap<>(ERROR_CODES);
    }

    /**
     * Определение кода ошибки
     */
    public static class ErrorCodeDefinition {
        private final String code;
        private final String userMessage;
        private final String technicalMessage;
        private final HttpStatus httpStatus;
        private final ExceptionContext.ExceptionCategory category;
        private final ExceptionContext.SeverityLevel severity;
        private final String actionRequired;

        public ErrorCodeDefinition(String code, String userMessage, String technicalMessage,
                HttpStatus httpStatus, ExceptionContext.ExceptionCategory category,
                ExceptionContext.SeverityLevel severity, String actionRequired) {
            this.code = code;
            this.userMessage = userMessage;
            this.technicalMessage = technicalMessage;
            this.httpStatus = httpStatus;
            this.category = category;
            this.severity = severity;
            this.actionRequired = actionRequired;
        }

        // Геттеры
        public String getCode() {
            return code;
        }

        public String getUserMessage() {
            return userMessage;
        }

        public String getTechnicalMessage() {
            return technicalMessage;
        }

        public HttpStatus getHttpStatus() {
            return httpStatus;
        }

        public ExceptionContext.ExceptionCategory getCategory() {
            return category;
        }

        public ExceptionContext.SeverityLevel getSeverity() {
            return severity;
        }

        public String getActionRequired() {
            return actionRequired;
        }

        @Override
        public String toString() {
            return String.format("ErrorCode[%s: %s (%s)]", code, userMessage, httpStatus);
        }
    }
}