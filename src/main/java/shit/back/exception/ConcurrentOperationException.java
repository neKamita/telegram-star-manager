package shit.back.exception;

/**
 * Исключение для случаев превышения лимита конкурентных операций
 */
public class ConcurrentOperationException extends BalanceException {

    private final Long userId;
    private final int currentOperations;
    private final int maxOperations;

    public ConcurrentOperationException(Long userId, int currentOperations, int maxOperations) {
        super(String.format("Превышено максимальное количество одновременных операций для пользователя %d. " +
                "Текущие операции: %d, максимум: %d", userId, currentOperations, maxOperations),
                "CONCURRENT_OPERATIONS_EXCEEDED");
        this.userId = userId;
        this.currentOperations = currentOperations;
        this.maxOperations = maxOperations;
    }

    public Long getUserId() {
        return userId;
    }

    public int getCurrentOperations() {
        return currentOperations;
    }

    public int getMaxOperations() {
        return maxOperations;
    }
}