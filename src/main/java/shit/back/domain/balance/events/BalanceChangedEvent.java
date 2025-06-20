package shit.back.domain.balance.events;

import org.springframework.context.ApplicationEvent;
import shit.back.entity.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Доменное событие изменения баланса пользователя
 * 
 * Генерируется при любом изменении баланса для обеспечения
 * асинхронной обработки и разрыва циклических зависимостей.
 * Перенесено в Domain Layer согласно принципам DDD.
 */
public class BalanceChangedEvent extends ApplicationEvent {

    /**
     * ID пользователя
     */
    private final Long userId;

    /**
     * Тип операции, приведшей к изменению баланса
     */
    private final TransactionType operationType;

    /**
     * Сумма операции
     */
    private final BigDecimal amount;

    /**
     * Баланс до операции
     */
    private final BigDecimal balanceBefore;

    /**
     * Баланс после операции
     */
    private final BigDecimal balanceAfter;

    /**
     * ID транзакции, связанной с изменением
     */
    private final String transactionId;

    /**
     * Описание операции
     */
    private final String description;

    /**
     * Время изменения баланса
     */
    private final LocalDateTime eventTimestamp;

    /**
     * ID заказа (если применимо)
     */
    private final String orderId;

    /**
     * Способ платежа (если применимо)
     */
    private final String paymentMethod;

    /**
     * Конструктор события
     * 
     * @param source         источник события (обычно агрегат)
     * @param userId         ID пользователя
     * @param operationType  тип операции
     * @param amount         сумма операции
     * @param balanceBefore  баланс до операции
     * @param balanceAfter   баланс после операции
     * @param transactionId  ID транзакции
     * @param description    описание операции
     * @param eventTimestamp время изменения
     * @param orderId        ID заказа
     * @param paymentMethod  способ платежа
     */
    public BalanceChangedEvent(Object source, Long userId, TransactionType operationType,
            BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter,
            String transactionId, String description, LocalDateTime eventTimestamp,
            String orderId, String paymentMethod) {
        super(source);
        this.userId = userId;
        this.operationType = operationType;
        this.amount = amount;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
        this.transactionId = transactionId;
        this.description = description;
        this.eventTimestamp = eventTimestamp;
        this.orderId = orderId;
        this.paymentMethod = paymentMethod;
    }

    /**
     * Упрощенный конструктор для основных операций
     * 
     * @param source        источник события
     * @param userId        ID пользователя
     * @param operationType тип операции
     * @param amount        сумма операции
     * @param balanceBefore баланс до операции
     * @param balanceAfter  баланс после операции
     * @param transactionId ID транзакции
     * @param description   описание операции
     */
    public BalanceChangedEvent(Object source, Long userId, TransactionType operationType,
            BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter,
            String transactionId, String description) {
        this(source, userId, operationType, amount, balanceBefore, balanceAfter,
                transactionId, description, LocalDateTime.now(), null, null);
    }

    // Геттеры
    public Long getUserId() {
        return userId;
    }

    public TransactionType getOperationType() {
        return operationType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceBefore() {
        return balanceBefore;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getEventTimestamp() {
        return eventTimestamp;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    /**
     * Проверка, является ли операция пополнением
     * 
     * @return true если это пополнение или возврат
     */
    public boolean isDeposit() {
        return operationType == TransactionType.DEPOSIT || operationType == TransactionType.REFUND;
    }

    /**
     * Проверка, является ли операция списанием
     * 
     * @return true если это покупка или списание
     */
    public boolean isWithdrawal() {
        return operationType == TransactionType.PURCHASE || operationType == TransactionType.WITHDRAWAL;
    }

    /**
     * Проверка, является ли операция административной корректировкой
     * 
     * @return true если это административная корректировка
     */
    public boolean isAdjustment() {
        return operationType == TransactionType.ADJUSTMENT;
    }

    /**
     * Получение размера изменения баланса (может быть отрицательным)
     * 
     * @return размер изменения баланса
     */
    public BigDecimal getBalanceChange() {
        return balanceAfter.subtract(balanceBefore);
    }

    /**
     * Проверка, является ли событие критическим (большая сумма или важная операция)
     */
    public boolean isCritical() {
        // Критическими считаем административные корректировки и операции свыше
        // определенной суммы
        return isAdjustment() || (amount != null && amount.compareTo(new BigDecimal("1000")) > 0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof BalanceChangedEvent))
            return false;
        BalanceChangedEvent that = (BalanceChangedEvent) o;
        return Objects.equals(userId, that.userId) &&
                operationType == that.operationType &&
                Objects.equals(amount, that.amount) &&
                Objects.equals(transactionId, that.transactionId) &&
                Objects.equals(eventTimestamp, that.eventTimestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, operationType, amount, transactionId, eventTimestamp);
    }

    @Override
    public String toString() {
        return String.format("BalanceChangedEvent{userId=%d, type=%s, amount=%s, change=%s, txId=%s}",
                userId, operationType, amount, getBalanceChange(), transactionId);
    }
}