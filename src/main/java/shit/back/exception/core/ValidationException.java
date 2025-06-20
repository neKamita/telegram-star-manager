package shit.back.exception.core;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * Исключение для ошибок валидации входных данных
 * Поддерживает множественные ошибки валидации с детальной информацией
 * 
 * @author TelegramStarManager
 * @since Week 3-4 Refactoring
 */
public class ValidationException extends BaseBusinessException {

    private final List<ValidationError> validationErrors;
    private final String validatedObject;
    private final ValidationType validationType;

    /**
     * Конструктор с одной ошибкой валидации
     */
    public ValidationException(String field,
            String invalidValue,
            String constraint,
            String validatedObject,
            ValidationType validationType) {
        super(
                generateErrorCode(validationType),
                String.format("Validation failed for field '%s' with value '%s': %s", field, invalidValue, constraint),
                "Ошибка валидации входных данных",
                ErrorSeverity.MEDIUM);

        this.validationErrors = List.of(new ValidationError(field, invalidValue, constraint));
        this.validatedObject = validatedObject;
        this.validationType = validationType;
    }

    /**
     * Конструктор с множественными ошибками валидации
     */
    public ValidationException(List<ValidationError> validationErrors,
            String validatedObject,
            ValidationType validationType) {
        super(
                generateErrorCode(validationType),
                String.format("Validation failed for %s with %d errors", validatedObject, validationErrors.size()),
                generateUserMessage(validationErrors.size()),
                ErrorSeverity.MEDIUM,
                createValidationContext(validationErrors, validatedObject),
                null);

        this.validationErrors = new ArrayList<>(validationErrors);
        this.validatedObject = validatedObject;
        this.validationType = validationType;
    }

    /**
     * Статический метод для создания исключения с одной ошибкой
     */
    public static ValidationException singleError(String field, String invalidValue, String constraint) {
        return new ValidationException(field, invalidValue, constraint, "Unknown", ValidationType.BUSINESS_RULE);
    }

    /**
     * Статический метод для создания исключения обязательного поля
     */
    public static ValidationException requiredField(String field, String objectName) {
        return new ValidationException(
                field,
                "null",
                "Поле обязательно для заполнения",
                objectName,
                ValidationType.REQUIRED_FIELD);
    }

    /**
     * Статический метод для создания исключения формата данных
     */
    public static ValidationException invalidFormat(String field, String value, String expectedFormat) {
        return new ValidationException(
                field,
                value,
                String.format("Ожидаемый формат: %s", expectedFormat),
                "Input",
                ValidationType.FORMAT_ERROR);
    }

    /**
     * Статический метод для создания исключения диапазона значений
     */
    public static ValidationException outOfRange(String field, String value, String range) {
        return new ValidationException(
                field,
                value,
                String.format("Значение должно быть в диапазоне: %s", range),
                "Input",
                ValidationType.RANGE_ERROR);
    }

    /**
     * Статический метод для создания исключения бизнес-правил
     */
    public static ValidationException businessRule(String rule, String details) {
        return new ValidationException(
                "businessRule",
                "violated",
                String.format("Нарушено бизнес-правило '%s': %s", rule, details),
                "BusinessLogic",
                ValidationType.BUSINESS_RULE);
    }

    // Геттеры
    public List<ValidationError> getValidationErrors() {
        return new ArrayList<>(validationErrors);
    }

    public String getValidatedObject() {
        return validatedObject;
    }

    public ValidationType getValidationType() {
        return validationType;
    }

    /**
     * Получить первую ошибку валидации
     */
    public ValidationError getFirstError() {
        return validationErrors.isEmpty() ? null : validationErrors.get(0);
    }

    /**
     * Проверить есть ли ошибки по конкретному полю
     */
    public boolean hasFieldError(String field) {
        return validationErrors.stream()
                .anyMatch(error -> error.getField().equals(field));
    }

    /**
     * Получить ошибки по конкретному полю
     */
    public List<ValidationError> getFieldErrors(String field) {
        return validationErrors.stream()
                .filter(error -> error.getField().equals(field))
                .toList();
    }

    /**
     * Получить количество ошибок валидации
     */
    public int getErrorCount() {
        return validationErrors.size();
    }

    /**
     * Генерация кода ошибки
     */
    private static String generateErrorCode(ValidationType validationType) {
        return "VAL_" + validationType.getCode();
    }

    /**
     * Генерация пользовательского сообщения
     */
    private static String generateUserMessage(int errorCount) {
        if (errorCount == 1) {
            return "Обнаружена ошибка валидации данных";
        } else {
            return String.format("Обнаружено %d ошибок валидации данных", errorCount);
        }
    }

    /**
     * Создание контекста с деталями валидации
     */
    private static Map<String, Object> createValidationContext(List<ValidationError> errors, String validatedObject) {
        return Map.of(
                "validationErrors", errors.stream()
                        .map(error -> Map.of(
                                "field", error.getField(),
                                "invalidValue", error.getInvalidValue(),
                                "constraint", error.getConstraint()))
                        .toList(),
                "validatedObject", validatedObject,
                "errorCount", errors.size());
    }

    /**
     * Класс для представления отдельной ошибки валидации
     */
    public static class ValidationError {
        private final String field;
        private final String invalidValue;
        private final String constraint;

        public ValidationError(String field, String invalidValue, String constraint) {
            this.field = field;
            this.invalidValue = invalidValue;
            this.constraint = constraint;
        }

        public String getField() {
            return field;
        }

        public String getInvalidValue() {
            return invalidValue;
        }

        public String getConstraint() {
            return constraint;
        }

        @Override
        public String toString() {
            return String.format("ValidationError[field='%s', value='%s', constraint='%s']",
                    field, invalidValue, constraint);
        }
    }

    /**
     * Типы валидации
     */
    public enum ValidationType {
        REQUIRED_FIELD("001", "Обязательное поле"),
        FORMAT_ERROR("002", "Ошибка формата"),
        RANGE_ERROR("003", "Ошибка диапазона"),
        BUSINESS_RULE("004", "Нарушение бизнес-правила"),
        CONSTRAINT_VIOLATION("005", "Нарушение ограничения"),
        DATA_INTEGRITY("006", "Нарушение целостности данных");

        private final String code;
        private final String description;

        ValidationType(String code, String description) {
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