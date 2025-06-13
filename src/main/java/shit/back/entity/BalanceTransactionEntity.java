package shit.back.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Сущность транзакций баланса пользователя
 * 
 * Хранит полную историю всех операций с балансом пользователя:
 * пополнения, покупки, возвраты и административные корректировки.
 */
@Entity
@Table(name = "balance_transactions", indexes = {
        @Index(name = "idx_balance_transactions_user_id", columnList = "user_id"),
        @Index(name = "idx_balance_transactions_transaction_id", columnList = "transaction_id", unique = true),
        @Index(name = "idx_balance_transactions_type", columnList = "type"),
        @Index(name = "idx_balance_transactions_status", columnList = "status"),
        @Index(name = "idx_balance_transactions_created_at", columnList = "created_at"),
        @Index(name = "idx_balance_transactions_order_id", columnList = "order_id"),
        @Index(name = "idx_balance_transactions_payment_method", columnList = "payment_method")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_balance_transactions_transaction_id", columnNames = "transaction_id")
})
@Data
@NoArgsConstructor
public class BalanceTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * ID пользователя - внешний ключ к UserSessionEntity
     */
    @Column(name = "user_id", nullable = false)
    @NotNull(message = "ID пользователя не может быть пустым")
    private Long userId;

    /**
     * Уникальный идентификатор транзакции
     * Генерируется автоматически при создании
     */
    @Column(name = "transaction_id", length = 36, nullable = false, unique = true)
    @NotBlank(message = "ID транзакции не может быть пустым")
    @Size(max = 36, message = "ID транзакции не может превышать 36 символов")
    private String transactionId;

    /**
     * Тип транзакции (пополнение, покупка, возврат и т.д.)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    @NotNull(message = "Тип транзакции не может быть пустым")
    private TransactionType type;

    /**
     * Сумма транзакции (всегда положительная)
     * Для дебетовых операций (списание) указывается положительная сумма
     */
    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    @NotNull(message = "Сумма транзакции не может быть пустой")
    @DecimalMin(value = "0.01", message = "Сумма транзакции должна быть положительной")
    @Digits(integer = 10, fraction = 2, message = "Неверный формат суммы транзакции")
    private BigDecimal amount;

    /**
     * Баланс до выполнения транзакции
     */
    @Column(name = "balance_before", precision = 12, scale = 2, nullable = false)
    @NotNull(message = "Баланс до транзакции не может быть пустым")
    @DecimalMin(value = "0.00", message = "Баланс до транзакции не может быть отрицательным")
    @Digits(integer = 10, fraction = 2, message = "Неверный формат баланса до транзакции")
    private BigDecimal balanceBefore;

    /**
     * Баланс после выполнения транзакции
     */
    @Column(name = "balance_after", precision = 12, scale = 2, nullable = false)
    @NotNull(message = "Баланс после транзакции не может быть пустым")
    @DecimalMin(value = "0.00", message = "Баланс после транзакции не может быть отрицательным")
    @Digits(integer = 10, fraction = 2, message = "Неверный формат баланса после транзакции")
    private BigDecimal balanceAfter;

    /**
     * Описание транзакции (назначение платежа, детали операции)
     */
    @Column(name = "description", length = 500)
    @Size(max = 500, message = "Описание транзакции не может превышать 500 символов")
    private String description;

    /**
     * ID заказа (если транзакция связана с заказом)
     * Внешний ключ к OrderEntity, nullable
     */
    @Column(name = "order_id", length = 8)
    @Size(max = 8, message = "ID заказа не может превышать 8 символов")
    private String orderId;

    /**
     * Статус транзакции
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @NotNull(message = "Статус транзакции не может быть пустым")
    private TransactionStatus status;

    /**
     * Способ платежа (Card, Bank Transfer, Crypto, etc.)
     */
    @Column(name = "payment_method", length = 50)
    @Size(max = 50, message = "Способ платежа не может превышать 50 символов")
    private String paymentMethod;

    /**
     * Детали платежа (JSON или строка с деталями транзакции)
     */
    @Column(name = "payment_details", length = 1000)
    @Size(max = 1000, message = "Детали платежа не могут превышать 1000 символов")
    private String paymentDetails;

    /**
     * Время создания транзакции
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Время завершения транзакции
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * Кто обработал транзакцию (система, админ, пользователь)
     */
    @Column(name = "processed_by", length = 100)
    @Size(max = 100, message = "Обработчик транзакции не может превышать 100 символов")
    private String processedBy = "SYSTEM";

    /**
     * Конструктор для создания новой транзакции
     */
    public BalanceTransactionEntity(Long userId, TransactionType type, BigDecimal amount,
            BigDecimal balanceBefore, BigDecimal balanceAfter) {
        this.userId = userId;
        this.transactionId = UUID.randomUUID().toString();
        this.type = type;
        this.amount = amount;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
        this.status = TransactionStatus.PENDING;
        this.processedBy = "SYSTEM";
    }

    /**
     * Конструктор с описанием
     */
    public BalanceTransactionEntity(Long userId, TransactionType type, BigDecimal amount,
            BigDecimal balanceBefore, BigDecimal balanceAfter,
            String description) {
        this(userId, type, amount, balanceBefore, balanceAfter);
        this.description = description;
    }

    /**
     * Конструктор для транзакций связанных с заказом
     */
    public BalanceTransactionEntity(Long userId, TransactionType type, BigDecimal amount,
            BigDecimal balanceBefore, BigDecimal balanceAfter,
            String description, String orderId) {
        this(userId, type, amount, balanceBefore, balanceAfter, description);
        this.orderId = orderId;
    }

    /**
     * Завершение транзакции
     */
    public void complete() {
        this.status = TransactionStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Отмена транзакции
     */
    public void cancel() {
        this.status = TransactionStatus.CANCELLED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Помечает транзакцию как неудачную
     */
    public void fail() {
        this.status = TransactionStatus.FAILED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Установка способа платежа и деталей
     */
    public void setPaymentInfo(String paymentMethod, String paymentDetails) {
        this.paymentMethod = paymentMethod;
        this.paymentDetails = paymentDetails;
    }

    /**
     * Проверка является ли транзакция завершенной
     */
    public boolean isCompleted() {
        return status == TransactionStatus.COMPLETED;
    }

    /**
     * Проверка является ли транзакция ожидающей
     */
    public boolean isPending() {
        return status == TransactionStatus.PENDING;
    }

    /**
     * Проверка является ли транзакция дебетовой (списание средств)
     */
    public boolean isDebit() {
        return type == TransactionType.PURCHASE || type == TransactionType.WITHDRAWAL;
    }

    /**
     * Проверка является ли транзакция кредитовой (пополнение средств)
     */
    public boolean isCredit() {
        return type == TransactionType.DEPOSIT || type == TransactionType.REFUND ||
                (type == TransactionType.ADJUSTMENT && balanceAfter.compareTo(balanceBefore) > 0);
    }

    /**
     * Получение типа операции с эмодзи
     */
    public String getTypeWithEmoji() {
        return switch (type) {
            case DEPOSIT -> "💰 Пополнение";
            case WITHDRAWAL -> "💸 Снятие";
            case PURCHASE -> "🛒 Покупка";
            case REFUND -> "↩️ Возврат";
            case ADJUSTMENT -> "⚙️ Корректировка";
        };
    }

    /**
     * Получение статуса с эмодзи
     */
    public String getStatusWithEmoji() {
        return switch (status) {
            case PENDING -> "⏳ Ожидает";
            case COMPLETED -> "✅ Завершено";
            case FAILED -> "❌ Не удалось";
            case CANCELLED -> "🚫 Отменено";
        };
    }

    /**
     * Форматированная сумма операции с учетом типа
     */
    public String getFormattedAmount() {
        String sign = isDebit() ? "-" : "+";
        return String.format("%s%.2f", sign, amount);
    }

    /**
     * Получение дельты баланса
     */
    public BigDecimal getBalanceDelta() {
        return balanceAfter.subtract(balanceBefore);
    }
}