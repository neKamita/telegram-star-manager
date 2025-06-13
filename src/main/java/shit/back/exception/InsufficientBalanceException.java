package shit.back.exception;

import java.math.BigDecimal;

/**
 * Исключение для случаев недостаточного баланса
 */
public class InsufficientBalanceException extends BalanceException {

    private final BigDecimal availableBalance;
    private final BigDecimal requiredAmount;

    public InsufficientBalanceException(BigDecimal availableBalance, BigDecimal requiredAmount) {
        super(String.format("Недостаточно средств на балансе. Доступно: %s, требуется: %s",
                availableBalance, requiredAmount), "INSUFFICIENT_BALANCE");
        this.availableBalance = availableBalance;
        this.requiredAmount = requiredAmount;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public BigDecimal getRequiredAmount() {
        return requiredAmount;
    }
}