package shit.back.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class UserSession {
    private Long userId;
    private String username;
    private String firstName;
    private String lastName;
    private SessionState state;
    private StarPackage selectedPackage;
    private String orderId;
    private LocalDateTime lastActivity;

    // === ПОЛЯ ДЛЯ ИНТЕГРАЦИИ С БАЛАНСОМ ===
    private java.math.BigDecimal pendingTopUpAmount; // Сумма пополнения в процессе
    private String paymentType; // Выбранный тип оплаты
    private java.math.BigDecimal balancePartAmount; // Часть оплаты балансом при смешанной оплате
    private String balanceTransactionId; // ID транзакции баланса

    public enum SessionState {
        IDLE,
        SELECTING_PACKAGE,
        CONFIRMING_ORDER,
        AWAITING_PAYMENT,
        PAYMENT_PROCESSING,
        COMPLETED,
        // === НОВЫЕ СОСТОЯНИЯ ДЛЯ ИНТЕГРАЦИИ С БАЛАНСОМ ===
        TOPPING_UP_BALANCE, // Пополнение баланса
        SELECTING_PAYMENT_TYPE, // Выбор типа оплаты (баланс/внешняя)
        BALANCE_PAYMENT_PROCESSING, // Обработка платежа балансом
        MIXED_PAYMENT_PROCESSING, // Обработка смешанного платежа
        ENTERING_CUSTOM_AMOUNT // Ввод пользовательской суммы пополнения
    }

    public UserSession(Long userId, String username, String firstName, String lastName) {
        this.userId = userId;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.state = SessionState.IDLE;
        this.lastActivity = LocalDateTime.now();
    }

    public void updateActivity() {
        this.lastActivity = LocalDateTime.now();
    }

    public String getDisplayName() {
        if (firstName != null && !firstName.isEmpty()) {
            return firstName + (lastName != null && !lastName.isEmpty() ? " " + lastName : "");
        }
        return username != null ? username : "Пользователь";
    }

    // === МЕТОДЫ ДЛЯ РАБОТЫ С БАЛАНСОМ ===

    /**
     * Проверяет, находится ли сессия в состоянии работы с балансом
     */
    public boolean isBalanceRelatedState() {
        return state == SessionState.TOPPING_UP_BALANCE ||
                state == SessionState.SELECTING_PAYMENT_TYPE ||
                state == SessionState.BALANCE_PAYMENT_PROCESSING ||
                state == SessionState.MIXED_PAYMENT_PROCESSING ||
                state == SessionState.ENTERING_CUSTOM_AMOUNT;
    }

    /**
     * Очищает данные, связанные с балансом
     */
    public void clearBalanceData() {
        this.pendingTopUpAmount = null;
        this.paymentType = null;
        this.balancePartAmount = null;
        this.balanceTransactionId = null;
    }

    /**
     * Устанавливает сумму пополнения
     */
    public void setPendingTopUpAmount(java.math.BigDecimal amount) {
        this.pendingTopUpAmount = amount;
        this.state = SessionState.TOPPING_UP_BALANCE;
        updateActivity();
    }

    /**
     * Устанавливает тип оплаты
     */
    public void setPaymentType(String type) {
        this.paymentType = type;
        this.state = SessionState.SELECTING_PAYMENT_TYPE;
        updateActivity();
    }

    /**
     * Устанавливает информацию о балансовой части смешанной оплаты
     */
    public void setBalancePartInfo(java.math.BigDecimal amount, String transactionId) {
        this.balancePartAmount = amount;
        this.balanceTransactionId = transactionId;
        this.state = SessionState.MIXED_PAYMENT_PROCESSING;
        updateActivity();
    }

    /**
     * Проверяет, готова ли сессия к оплате балансом
     */
    public boolean isReadyForBalancePayment() {
        return selectedPackage != null &&
                (state == SessionState.SELECTING_PAYMENT_TYPE ||
                        state == SessionState.BALANCE_PAYMENT_PROCESSING);
    }

    /**
     * Проверяет, находится ли в процессе смешанной оплаты
     */
    public boolean isMixedPaymentInProgress() {
        return state == SessionState.MIXED_PAYMENT_PROCESSING &&
                balancePartAmount != null;
    }
}
