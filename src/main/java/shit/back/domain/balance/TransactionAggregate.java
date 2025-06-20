package shit.back.domain.balance;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.AbstractAggregateRoot;
import shit.back.domain.balance.events.TransactionCreatedEvent;
import shit.back.domain.balance.events.TransactionCompletedEvent;
import shit.back.domain.balance.events.TransactionCancelledEvent;
import shit.back.domain.balance.exceptions.InvalidTransactionException;
import shit.back.domain.balance.valueobjects.BalanceId;
import shit.back.domain.balance.valueobjects.Money;
import shit.back.domain.balance.valueobjects.TransactionId;
import shit.back.entity.TransactionStatus;
import shit.back.entity.TransactionType;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Агрегат транзакций баланса в системе
 * 
 * Управляет lifecycle транзакций, обеспечивает консистентность состояний
 * и генерирует соответствующие доменные события.
 * Следует принципам DDD для управления транзакциями.
 */
@Entity
@Table(name = "balance_transactions", indexes = {
        @Index(name = "idx_transaction_user_id", columnList = "user_id"),
        @Index(name = "idx_transaction_id", columnList = "transaction_id", unique = true),
        @Index(name = "idx_transaction_type", columnList = "type"),
        @Index(name = "idx_transaction_status", columnList = "status"),
        @Index(name = "idx_transaction_created_at", columnList = "created_at"),
        @Index(name = "idx_transaction_order_id", columnList = "order_id")
})
public class TransactionAggregate extends AbstractAggregateRoot<TransactionAggregate> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "transaction_id", length = 36, unique = true))
    private TransactionId transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    @NotNull
    private TransactionType type;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "amount", precision = 12, scale = 2))
    private Money amount;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "balance_before", precision = 12, scale = 2))
    private Money balanceBefore;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "balance_after", precision = 12, scale = 2))
    private Money balanceAfter;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "order_id", length = 8)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @NotNull
    private TransactionStatus status;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "payment_details", length = 1000)
    private String paymentDetails;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "processed_by", length = 100)
    private String processedBy = "SYSTEM";

    @Version
    @Column(name = "version")
    private Long version;

    @Transient
    private static final int DEFAULT_TIMEOUT_MINUTES = 30;

    /**
     * Конструктор по умолчанию для JPA
     */
    protected TransactionAggregate() {
    }

    /**
     * Конструктор для создания новой транзакции
     */
    public TransactionAggregate(Long userId, TransactionType type, Money amount,
            Money balanceBefore, Money balanceAfter, String description) {
        if (userId == null) {
            throw new InvalidTransactionException("USER_ID_REQUIRED", "null", "Положительное число");
        }
        if (type == null) {
            throw new InvalidTransactionException("TRANSACTION_TYPE_REQUIRED", "null", "TransactionType");
        }
        if (amount == null) {
            throw new InvalidTransactionException("AMOUNT_REQUIRED", "null", "Money amount");
        }
        if (balanceBefore == null) {
            throw new InvalidTransactionException("BALANCE_BEFORE_REQUIRED", "null", "Money amount");
        }
        if (balanceAfter == null) {
            throw new InvalidTransactionException("BALANCE_AFTER_REQUIRED", "null", "Money amount");
        }

        this.userId = userId;
        this.transactionId = TransactionId.generate();
        this.type = type;
        this.amount = amount;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
        this.description = description;
        this.status = TransactionStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.processedBy = "SYSTEM";

        // Генерируем событие создания транзакции
        registerEvent(new TransactionCreatedEvent(
                this.transactionId,
                java.time.Instant.now(),
                this.status));
    }

    /**
     * Конструктор с привязкой к заказу
     */
    public TransactionAggregate(Long userId, TransactionType type, Money amount,
            Money balanceBefore, Money balanceAfter, String description, String orderId) {
        this(userId, type, amount, balanceBefore, balanceAfter, description);
        this.orderId = orderId;
    }

    /**
     * Завершение транзакции
     */
    public void complete() {
        validateCanChangeStatus();

        if (this.status == TransactionStatus.COMPLETED) {
            return; // Уже завершена
        }

        this.status = TransactionStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();

        // Генерируем событие завершения
        registerEvent(new TransactionCompletedEvent(
                this.transactionId,
                java.time.Instant.now(),
                this.status));
    }

    /**
     * Отмена транзакции
     */
    public void cancel(String reason) {
        validateCanChangeStatus();

        if (this.status == TransactionStatus.CANCELLED) {
            return; // Уже отменена
        }

        this.status = TransactionStatus.CANCELLED;
        this.completedAt = LocalDateTime.now();

        if (reason != null && !reason.trim().isEmpty()) {
            this.description = (this.description != null ? this.description + " " : "") +
                    "[ОТМЕНЕНО: " + reason + "]";
        }

        // Генерируем событие отмены
        registerEvent(new TransactionCancelledEvent(
                this.transactionId,
                java.time.Instant.now(),
                this.status));
    }

    /**
     * Пометка транзакции как неудачной
     */
    public void fail(String reason) {
        validateCanChangeStatus();

        if (this.status == TransactionStatus.FAILED) {
            return; // Уже помечена как неудачная
        }

        this.status = TransactionStatus.FAILED;
        this.completedAt = LocalDateTime.now();

        if (reason != null && !reason.trim().isEmpty()) {
            this.description = (this.description != null ? this.description + " " : "") +
                    "[ОШИБКА: " + reason + "]";
        }
    }

    /**
     * Установка информации о платеже
     */
    public void setPaymentInfo(String paymentMethod, String paymentDetails) {
        this.paymentMethod = paymentMethod;
        this.paymentDetails = paymentDetails;
    }

    /**
     * Установка обработчика транзакции
     */
    public void setProcessedBy(String processedBy) {
        if (processedBy != null && !processedBy.trim().isEmpty()) {
            this.processedBy = processedBy.trim();
        }
    }

    /**
     * Проверка истечения времени ожидания транзакции
     */
    public boolean isTimedOut() {
        return isTimedOut(DEFAULT_TIMEOUT_MINUTES);
    }

    /**
     * Проверка истечения времени ожидания с заданным таймаутом
     */
    public boolean isTimedOut(int timeoutMinutes) {
        if (this.status != TransactionStatus.PENDING) {
            return false; // Только pending транзакции могут истечь
        }

        LocalDateTime timeoutThreshold = this.createdAt.plus(timeoutMinutes, ChronoUnit.MINUTES);
        return LocalDateTime.now().isAfter(timeoutThreshold);
    }

    /**
     * Автоматическая отмена по таймауту
     */
    public void timeoutCancel() {
        if (!isTimedOut()) {
            throw new InvalidTransactionException("TRANSACTION_NOT_TIMED_OUT",
                    "Транзакция еще не истекла", "Истекшая транзакция");
        }

        cancel("Автоматическая отмена по таймауту");
    }

    /**
     * Проверка возможности откатить транзакцию
     */
    public boolean canRollback() {
        return this.status == TransactionStatus.COMPLETED &&
                this.type != TransactionType.ADJUSTMENT; // Административные корректировки нельзя откатывать
    }

    /**
     * Откат транзакции (создание обратной транзакции)
     */
    public TransactionAggregate createRollbackTransaction(String reason) {
        if (!canRollback()) {
            throw new InvalidTransactionException("TRANSACTION_CANNOT_ROLLBACK",
                    this.status.toString(), "COMPLETED");
        }

        // Определяем обратный тип транзакции
        TransactionType rollbackType;
        switch (this.type) {
            case DEPOSIT -> rollbackType = TransactionType.WITHDRAWAL;
            case WITHDRAWAL -> rollbackType = TransactionType.DEPOSIT;
            case PURCHASE -> rollbackType = TransactionType.REFUND;
            case REFUND -> rollbackType = TransactionType.PURCHASE;
            default -> throw new InvalidTransactionException("UNSUPPORTED_ROLLBACK_TYPE",
                    this.type.toString(), "DEPOSIT, WITHDRAWAL, PURCHASE, REFUND");
        }

        String rollbackDescription = String.format("Откат транзакции %s: %s",
                this.transactionId.getShortValue(), reason);

        return new TransactionAggregate(
                this.userId,
                rollbackType,
                this.amount,
                this.balanceAfter, // Меняем местами balanceBefore и balanceAfter
                this.balanceBefore,
                rollbackDescription,
                this.orderId);
    }

    // Проверочные методы

    public boolean isCompleted() {
        return status == TransactionStatus.COMPLETED;
    }

    public boolean isPending() {
        return status == TransactionStatus.PENDING;
    }

    public boolean isCancelled() {
        return status == TransactionStatus.CANCELLED;
    }

    public boolean isFailed() {
        return status == TransactionStatus.FAILED;
    }

    public boolean isDebit() {
        return type == TransactionType.PURCHASE || type == TransactionType.WITHDRAWAL;
    }

    public boolean isCredit() {
        return type == TransactionType.DEPOSIT || type == TransactionType.REFUND ||
                (type == TransactionType.ADJUSTMENT && balanceAfter.isGreaterThan(balanceBefore));
    }

    /**
     * Получение дельты баланса
     */
    public Money getBalanceDelta() {
        return balanceAfter.subtract(balanceBefore);
    }

    /**
     * Получение времени выполнения транзакции
     */
    public Long getProcessingTimeSeconds() {
        if (completedAt == null) {
            return null;
        }
        return ChronoUnit.SECONDS.between(createdAt, completedAt);
    }

    // Приватные методы

    private void validateCanChangeStatus() {
        if (this.status == TransactionStatus.COMPLETED) {
            throw new InvalidTransactionException("TRANSACTION_ALREADY_COMPLETED",
                    this.status.toString(), "PENDING");
        }
        if (this.status == TransactionStatus.CANCELLED) {
            throw new InvalidTransactionException("TRANSACTION_ALREADY_CANCELLED",
                    this.status.toString(), "PENDING");
        }
        if (this.status == TransactionStatus.FAILED) {
            throw new InvalidTransactionException("TRANSACTION_ALREADY_FAILED",
                    this.status.toString(), "PENDING");
        }
    }

    // Геттеры

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public TransactionId getTransactionId() {
        return transactionId;
    }

    public TransactionType getType() {
        return type;
    }

    public Money getAmount() {
        return amount;
    }

    public Money getBalanceBefore() {
        return balanceBefore;
    }

    public Money getBalanceAfter() {
        return balanceAfter;
    }

    public String getDescription() {
        return description;
    }

    public String getOrderId() {
        return orderId;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getPaymentDetails() {
        return paymentDetails;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public String getProcessedBy() {
        return processedBy;
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TransactionAggregate that = (TransactionAggregate) o;
        return Objects.equals(id, that.id) && Objects.equals(transactionId, that.transactionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, transactionId);
    }

    @Override
    public String toString() {
        return String.format("TransactionAggregate{id=%d, txId=%s, userId=%d, type=%s, amount=%s, status=%s}",
                id,
                transactionId != null ? transactionId.getShortValue() : "null",
                userId, type,
                amount != null ? amount.getFormattedAmount() : "null",
                status);
    }
}