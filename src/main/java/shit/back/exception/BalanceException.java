package shit.back.exception;

/**
 * Базовое исключение для операций с балансом
 * 
 * Используется для всех ошибок, связанных с системой баланса пользователей.
 */
public class BalanceException extends RuntimeException {

    private final String errorCode;

    public BalanceException(String message) {
        super(message);
        this.errorCode = "BALANCE_ERROR";
    }

    public BalanceException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public BalanceException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "BALANCE_ERROR";
    }

    public BalanceException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}