package shit.back.exception;

/**
 * Исключение для случаев некорректных транзакций
 */
public class InvalidTransactionException extends BalanceException {

    private final String transactionId;
    private final String reason;

    public InvalidTransactionException(String transactionId, String reason) {
        super(String.format("Некорректная транзакция %s: %s", transactionId, reason),
                "INVALID_TRANSACTION");
        this.transactionId = transactionId;
        this.reason = reason;
    }

    public InvalidTransactionException(String reason) {
        super("Некорректная транзакция: " + reason, "INVALID_TRANSACTION");
        this.transactionId = null;
        this.reason = reason;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getReason() {
        return reason;
    }
}