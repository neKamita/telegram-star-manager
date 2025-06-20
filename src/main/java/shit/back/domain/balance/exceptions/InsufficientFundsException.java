package shit.back.domain.balance.exceptions;

import shit.back.domain.balance.valueobjects.BalanceId;
import shit.back.domain.balance.valueobjects.Money;

/**
 * Доменное исключение для случаев недостаточности средств на балансе
 * 
 * Инкапсулирует бизнес-логику проверки достаточности средств
 * и предоставляет детальную информацию об ошибке.
 */
public class InsufficientFundsException extends BalanceDomainException {

    private final BalanceId balanceId;
    private final Money currentBalance;
    private final Money requestedAmount;
    private final Money shortfallAmount;

    /**
     * Конструктор с полной информацией о недостаточности средств
     * 
     * @param balanceId       ID баланса
     * @param currentBalance  текущий баланс
     * @param requestedAmount запрашиваемая сумма
     */
    public InsufficientFundsException(BalanceId balanceId, Money currentBalance, Money requestedAmount) {
        super(buildMessage(balanceId, currentBalance, requestedAmount));
        this.balanceId = balanceId;
        this.currentBalance = currentBalance;
        this.requestedAmount = requestedAmount;
        this.shortfallAmount = requestedAmount.subtract(currentBalance);
    }

    /**
     * Конструктор с дополнительным сообщением
     */
    public InsufficientFundsException(BalanceId balanceId, Money currentBalance, Money requestedAmount,
            String additionalMessage) {
        super(buildMessage(balanceId, currentBalance, requestedAmount) + ". " + additionalMessage);
        this.balanceId = balanceId;
        this.currentBalance = currentBalance;
        this.requestedAmount = requestedAmount;
        this.shortfallAmount = requestedAmount.subtract(currentBalance);
    }

    /**
     * Конструктор с причиной исключения
     */
    public InsufficientFundsException(BalanceId balanceId, Money currentBalance, Money requestedAmount,
            Throwable cause) {
        super(buildMessage(balanceId, currentBalance, requestedAmount), cause);
        this.balanceId = balanceId;
        this.currentBalance = currentBalance;
        this.requestedAmount = requestedAmount;
        this.shortfallAmount = requestedAmount.subtract(currentBalance);
    }

    /**
     * Упрощенный конструктор для базовой ошибки недостаточности средств
     */
    public InsufficientFundsException(Money currentBalance, Money requestedAmount) {
        this(null, currentBalance, requestedAmount);
    }

    /**
     * Построение сообщения об ошибке
     */
    private static String buildMessage(BalanceId balanceId, Money currentBalance, Money requestedAmount) {
        StringBuilder message = new StringBuilder();
        message.append("Недостаточно средств на балансе");

        if (balanceId != null) {
            message.append(" (ID: ").append(balanceId.getValue()).append(")");
        }

        message.append(": требуется ").append(requestedAmount.getFormattedAmount())
                .append(", доступно ").append(currentBalance.getFormattedAmount());

        Money shortfall = requestedAmount.subtract(currentBalance);
        message.append(", недостает ").append(shortfall.getFormattedAmount());

        return message.toString();
    }

    /**
     * Получение ID баланса
     */
    public BalanceId getBalanceId() {
        return balanceId;
    }

    /**
     * Получение текущего баланса
     */
    public Money getCurrentBalance() {
        return currentBalance;
    }

    /**
     * Получение запрашиваемой суммы
     */
    public Money getRequestedAmount() {
        return requestedAmount;
    }

    /**
     * Получение суммы недостачи
     */
    public Money getShortfallAmount() {
        return shortfallAmount;
    }

    /**
     * Проверка, является ли недостача критической (больше определенного порога)
     */
    public boolean isCriticalShortfall() {
        return shortfallAmount.isGreaterThan(currentBalance);
    }

    /**
     * Получение процента недостачи от запрашиваемой суммы
     */
    public double getShortfallPercentage() {
        if (requestedAmount.isZero()) {
            return 0.0;
        }
        return shortfallAmount.getAmount().doubleValue() / requestedAmount.getAmount().doubleValue() * 100.0;
    }

    /**
     * Получение детального описания ошибки для логирования
     */
    public String getDetailedMessage() {
        return String.format(
                "InsufficientFundsException: BalanceId=%s, Current=%s, Requested=%s, Shortfall=%s (%.2f%%)",
                balanceId != null ? balanceId.getValue() : "N/A",
                currentBalance.getFormattedAmount(),
                requestedAmount.getFormattedAmount(),
                shortfallAmount.getFormattedAmount(),
                getShortfallPercentage());
    }

    @Override
    public String getErrorCode() {
        return "BALANCE_INSUFFICIENT_FUNDS";
    }

    @Override
    public String getUserFriendlyMessage() {
        return String.format(
                "На вашем балансе недостаточно средств. Требуется %s, доступно %s",
                requestedAmount.getFormattedAmount(),
                currentBalance.getFormattedAmount());
    }
}