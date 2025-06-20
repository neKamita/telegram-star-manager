package shit.back.domain.balance.exceptions;

import shit.back.domain.balance.valueobjects.TransactionId;
import shit.back.entity.TransactionType;

/**
 * Доменное исключение для случаев некорректных транзакций
 * 
 * Инкапсулирует бизнес-правила валидации транзакций
 * и предоставляет детальную информацию об ошибке.
 */
public class InvalidTransactionException extends BalanceDomainException {

    private final TransactionId transactionId;
    private final TransactionType transactionType;
    private final String validationRule;
    private final String actualValue;
    private final String expectedValue;

    /**
     * Конструктор с полной информацией о некорректной транзакции
     * 
     * @param transactionId   ID транзакции
     * @param transactionType тип транзакции
     * @param validationRule  нарушенное правило валидации
     * @param actualValue     фактическое значение
     * @param expectedValue   ожидаемое значение
     */
    public InvalidTransactionException(TransactionId transactionId, TransactionType transactionType,
            String validationRule, String actualValue, String expectedValue) {
        super(buildMessage(transactionId, transactionType, validationRule, actualValue, expectedValue));
        this.transactionId = transactionId;
        this.transactionType = transactionType;
        this.validationRule = validationRule;
        this.actualValue = actualValue;
        this.expectedValue = expectedValue;
    }

    /**
     * Конструктор с дополнительным сообщением
     */
    public InvalidTransactionException(TransactionId transactionId, TransactionType transactionType,
            String validationRule, String additionalMessage) {
        super(buildMessage(transactionId, transactionType, validationRule, null, null) + ". " + additionalMessage);
        this.transactionId = transactionId;
        this.transactionType = transactionType;
        this.validationRule = validationRule;
        this.actualValue = null;
        this.expectedValue = null;
    }

    /**
     * Конструктор с причиной исключения
     */
    public InvalidTransactionException(TransactionId transactionId, TransactionType transactionType,
            String validationRule, Throwable cause) {
        super(buildMessage(transactionId, transactionType, validationRule, null, null), cause);
        this.transactionId = transactionId;
        this.transactionType = transactionType;
        this.validationRule = validationRule;
        this.actualValue = null;
        this.expectedValue = null;
    }

    /**
     * Упрощенный конструктор для базовой ошибки валидации
     */
    public InvalidTransactionException(String validationRule, String actualValue, String expectedValue) {
        this(null, null, validationRule, actualValue, expectedValue);
    }

    /**
     * Конструктор только с правилом валидации
     */
    public InvalidTransactionException(String validationRule) {
        this(null, null, validationRule, null, null);
    }

    /**
     * Построение сообщения об ошибке
     */
    private static String buildMessage(TransactionId transactionId, TransactionType transactionType,
            String validationRule, String actualValue, String expectedValue) {
        StringBuilder message = new StringBuilder();
        message.append("Некорректная транзакция");

        if (transactionId != null) {
            message.append(" (ID: ").append(transactionId.getValue()).append(")");
        }

        if (transactionType != null) {
            message.append(" типа ").append(transactionType);
        }

        message.append(": нарушено правило '").append(validationRule).append("'");

        if (actualValue != null && expectedValue != null) {
            message.append(". Получено: ").append(actualValue)
                    .append(", ожидалось: ").append(expectedValue);
        }

        return message.toString();
    }

    /**
     * Получение ID транзакции
     */
    public TransactionId getTransactionId() {
        return transactionId;
    }

    /**
     * Получение типа транзакции
     */
    public TransactionType getTransactionType() {
        return transactionType;
    }

    /**
     * Получение нарушенного правила валидации
     */
    public String getValidationRule() {
        return validationRule;
    }

    /**
     * Получение фактического значения
     */
    public String getActualValue() {
        return actualValue;
    }

    /**
     * Получение ожидаемого значения
     */
    public String getExpectedValue() {
        return expectedValue;
    }

    /**
     * Проверка, является ли ошибка связанной с форматом данных
     */
    public boolean isFormatError() {
        return validationRule != null &&
                (validationRule.toLowerCase().contains("format") ||
                        validationRule.toLowerCase().contains("формат"));
    }

    /**
     * Проверка, является ли ошибка связанной с бизнес-правилами
     */
    public boolean isBusinessRuleError() {
        return validationRule != null &&
                (validationRule.toLowerCase().contains("business") ||
                        validationRule.toLowerCase().contains("бизнес"));
    }

    /**
     * Проверка, является ли ошибка связанной с ограничениями суммы
     */
    public boolean isAmountLimitError() {
        return validationRule != null &&
                (validationRule.toLowerCase().contains("amount") ||
                        validationRule.toLowerCase().contains("сумм"));
    }

    /**
     * Получение детального описания ошибки для логирования
     */
    public String getDetailedMessage() {
        return String.format(
                "InvalidTransactionException: TransactionId=%s, Type=%s, Rule='%s', Actual='%s', Expected='%s'",
                transactionId != null ? transactionId.getValue() : "N/A",
                transactionType != null ? transactionType : "N/A",
                validationRule != null ? validationRule : "N/A",
                actualValue != null ? actualValue : "N/A",
                expectedValue != null ? expectedValue : "N/A");
    }

    /**
     * Получение краткого описания ошибки
     */
    public String getShortMessage() {
        return String.format("Ошибка валидации: %s", validationRule);
    }

    @Override
    public String getErrorCode() {
        if (isFormatError()) {
            return "TRANSACTION_INVALID_FORMAT";
        } else if (isBusinessRuleError()) {
            return "TRANSACTION_BUSINESS_RULE_VIOLATION";
        } else if (isAmountLimitError()) {
            return "TRANSACTION_AMOUNT_LIMIT_EXCEEDED";
        } else {
            return "TRANSACTION_INVALID";
        }
    }

    @Override
    public String getUserFriendlyMessage() {
        if (isFormatError()) {
            return "Неверный формат данных транзакции";
        } else if (isBusinessRuleError()) {
            return "Транзакция нарушает бизнес-правила системы";
        } else if (isAmountLimitError()) {
            return "Сумма транзакции превышает допустимые лимиты";
        } else {
            return String.format("Некорректная транзакция: %s", validationRule);
        }
    }

    @Override
    public boolean isCritical() {
        // Бизнес-правила считаются критическими ошибками
        return isBusinessRuleError();
    }
}