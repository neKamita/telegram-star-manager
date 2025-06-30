package shit.back.domain.dualBalance.exceptions;

import shit.back.domain.balance.valueobjects.Money;
import shit.back.domain.dualBalance.valueobjects.BalanceType;
import shit.back.domain.dualBalance.valueobjects.DualBalanceId;

/**
 * Доменное исключение для ошибок трансфера между балансами
 * 
 * Инкапсулирует бизнес-логику валидации трансферов
 * и предоставляет детальную информацию об ошибке.
 */
public class BalanceTransferException extends DualBalanceDomainException {

    private final DualBalanceId dualBalanceId;
    private final BalanceType fromType;
    private final BalanceType toType;
    private final Money transferAmount;
    private final Money availableAmount;
    private final String transferRule;

    /**
     * Конструктор с полной информацией о проблеме трансфера
     */
    public BalanceTransferException(DualBalanceId dualBalanceId, BalanceType fromType, BalanceType toType,
            Money transferAmount, Money availableAmount, String transferRule) {
        super(buildMessage(dualBalanceId, fromType, toType, transferAmount, availableAmount, transferRule));
        this.dualBalanceId = dualBalanceId;
        this.fromType = fromType;
        this.toType = toType;
        this.transferAmount = transferAmount;
        this.availableAmount = availableAmount;
        this.transferRule = transferRule;
    }

    /**
     * Конструктор с дополнительным сообщением
     */
    public BalanceTransferException(DualBalanceId dualBalanceId, BalanceType fromType, BalanceType toType,
            String transferRule, String additionalMessage) {
        super(buildMessage(dualBalanceId, fromType, toType, null, null, transferRule) + ". " + additionalMessage);
        this.dualBalanceId = dualBalanceId;
        this.fromType = fromType;
        this.toType = toType;
        this.transferAmount = null;
        this.availableAmount = null;
        this.transferRule = transferRule;
    }

    /**
     * Упрощенный конструктор для базовой ошибки трансфера
     */
    public BalanceTransferException(String transferRule, String message) {
        super(message);
        this.dualBalanceId = null;
        this.fromType = null;
        this.toType = null;
        this.transferAmount = null;
        this.availableAmount = null;
        this.transferRule = transferRule;
    }

    /**
     * Построение сообщения об ошибке
     */
    private static String buildMessage(DualBalanceId dualBalanceId, BalanceType fromType, BalanceType toType,
            Money transferAmount, Money availableAmount, String transferRule) {
        StringBuilder message = new StringBuilder();
        message.append("Ошибка трансфера между балансами");

        if (dualBalanceId != null) {
            message.append(" (ID: ").append(dualBalanceId.getValue()).append(")");
        }

        if (fromType != null && toType != null) {
            message.append(" с ").append(fromType.getDisplayName())
                    .append(" на ").append(toType.getDisplayName());
        }

        if (transferAmount != null) {
            message.append(": сумма ").append(transferAmount.getFormattedAmount());
        }

        if (availableAmount != null) {
            message.append(", доступно ").append(availableAmount.getFormattedAmount());
        }

        message.append(". Нарушено правило: ").append(transferRule);

        return message.toString();
    }

    /**
     * Получение ID двойного баланса
     */
    public DualBalanceId getDualBalanceId() {
        return dualBalanceId;
    }

    /**
     * Получение типа исходного баланса
     */
    public BalanceType getFromType() {
        return fromType;
    }

    /**
     * Получение типа целевого баланса
     */
    public BalanceType getToType() {
        return toType;
    }

    /**
     * Получение суммы трансфера
     */
    public Money getTransferAmount() {
        return transferAmount;
    }

    /**
     * Получение доступной суммы
     */
    public Money getAvailableAmount() {
        return availableAmount;
    }

    /**
     * Получение нарушенного правила трансфера
     */
    public String getTransferRule() {
        return transferRule;
    }

    /**
     * Проверка, является ли ошибка связанной с недостатком средств
     */
    public boolean isInsufficientFunds() {
        return transferAmount != null && availableAmount != null &&
                transferAmount.isGreaterThan(availableAmount);
    }

    /**
     * Получение суммы недостачи
     */
    public Money getShortfallAmount() {
        if (!isInsufficientFunds()) {
            return null;
        }
        return transferAmount.subtract(availableAmount);
    }

    @Override
    public String getErrorCode() {
        if (isInsufficientFunds()) {
            return "DUAL_BALANCE_INSUFFICIENT_FUNDS";
        }
        return "DUAL_BALANCE_TRANSFER_FAILED";
    }

    @Override
    public String getUserFriendlyMessage() {
        if (isInsufficientFunds()) {
            return String.format("Недостаточно средств для трансфера. Требуется %s, доступно %s",
                    transferAmount.getFormattedAmount(), availableAmount.getFormattedAmount());
        }
        return "Не удалось выполнить трансфер между балансами: " + transferRule;
    }

    @Override
    public boolean isCritical() {
        return transferRule != null && transferRule.toLowerCase().contains("critical");
    }
}