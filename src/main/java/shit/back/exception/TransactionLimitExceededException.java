package shit.back.exception;

import java.math.BigDecimal;

/**
 * Исключение для случаев превышения лимитов транзакций
 */
public class TransactionLimitExceededException extends BalanceException {

    private final BigDecimal currentAmount;
    private final BigDecimal limitAmount;
    private final String limitType;

    public TransactionLimitExceededException(String limitType, BigDecimal currentAmount, BigDecimal limitAmount) {
        super(String.format("Превышен лимит %s. Текущая сумма: %s, лимит: %s",
                limitType, currentAmount, limitAmount), "TRANSACTION_LIMIT_EXCEEDED");
        this.limitType = limitType;
        this.currentAmount = currentAmount;
        this.limitAmount = limitAmount;
    }

    public BigDecimal getCurrentAmount() {
        return currentAmount;
    }

    public BigDecimal getLimitAmount() {
        return limitAmount;
    }

    public String getLimitType() {
        return limitType;
    }
}