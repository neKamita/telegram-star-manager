package shit.back.exception;

/**
 * Исключение для случаев неавторизованных административных операций
 */
public class UnauthorizedAdminOperationException extends BalanceException {

    private final String adminUser;
    private final String operation;

    public UnauthorizedAdminOperationException(String adminUser, String operation) {
        super(String.format("Неавторизованная административная операция. Пользователь: %s, операция: %s",
                adminUser, operation), "UNAUTHORIZED_ADMIN_OPERATION");
        this.adminUser = adminUser;
        this.operation = operation;
    }

    public String getAdminUser() {
        return adminUser;
    }

    public String getOperation() {
        return operation;
    }
}