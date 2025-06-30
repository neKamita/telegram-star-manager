package shit.back.domain.dualBalance;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.AbstractAggregateRoot;
import shit.back.domain.balance.valueobjects.Money;
import shit.back.domain.balance.valueobjects.TransactionId;
import shit.back.domain.dualBalance.events.BalanceTransferCompletedEvent;
import shit.back.domain.dualBalance.events.BalanceTransferInitiatedEvent;
import shit.back.domain.dualBalance.exceptions.BalanceTransferException;
import shit.back.domain.dualBalance.valueobjects.BalanceType;
import shit.back.domain.dualBalance.valueobjects.DualBalanceId;
import shit.back.entity.TransactionStatus;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Агрегат трансферов между балансами
 * 
 * Управляет переводами между bank и main балансами.
 * Обеспечивает атомарность операций трансфера и возможность отката
 * незавершенных операций.
 * Генерирует события успешного/неуспешного трансфера.
 */
@Entity
@Table(name = "balance_transfers", indexes = {
        @Index(name = "idx_transfer_dual_balance_id", columnList = "dual_balance_id"),
        @Index(name = "idx_transfer_transaction_id", columnList = "transaction_id", unique = true),
        @Index(name = "idx_transfer_status", columnList = "status"),
        @Index(name = "idx_transfer_created_at", columnList = "created_at"),
        @Index(name = "idx_transfer_from_type", columnList = "from_type"),
        @Index(name = "idx_transfer_to_type", columnList = "to_type")
})
public class BalanceTransferAggregate extends AbstractAggregateRoot<BalanceTransferAggregate> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "dual_balance_id", nullable = false)
    private Long dualBalanceId;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "transaction_id", length = 36, unique = true))
    private TransactionId transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_type", nullable = false, length = 10)
    @NotNull
    private BalanceType fromType;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_type", nullable = false, length = 10)
    @NotNull
    private BalanceType toType;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "transfer_amount", precision = 12, scale = 2))
    private Money transferAmount;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "from_balance_before", precision = 12, scale = 2))
    private Money fromBalanceBefore;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "from_balance_after", precision = 12, scale = 2))
    private Money fromBalanceAfter;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "to_balance_before", precision = 12, scale = 2))
    private Money toBalanceBefore;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "to_balance_after", precision = 12, scale = 2))
    private Money toBalanceAfter;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @NotNull
    private TransactionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Version
    @Column(name = "version")
    private Long version;

    @Transient
    private static final int DEFAULT_TIMEOUT_MINUTES = 15;

    /**
     * Конструктор по умолчанию для JPA
     */
    protected BalanceTransferAggregate() {
    }

    /**
     * Конструктор для создания нового трансфера
     */
    public BalanceTransferAggregate(Long dualBalanceId, Long userId, BalanceType fromType, BalanceType toType,
            Money transferAmount, Money fromBalanceBefore, Money toBalanceBefore, String description) {
        if (dualBalanceId == null) {
            throw new BalanceTransferException("DUAL_BALANCE_ID_REQUIRED", "ID двойного баланса не может быть null");
        }
        if (userId == null) {
            throw new BalanceTransferException("USER_ID_REQUIRED", "ID пользователя не может быть null");
        }
        if (fromType == null || toType == null) {
            throw new BalanceTransferException("BALANCE_TYPE_REQUIRED", "Типы балансов не могут быть null");
        }
        if (fromType == toType) {
            throw new BalanceTransferException("SAME_BALANCE_TYPE",
                    "Нельзя переводить между одинаковыми типами балансов");
        }
        if (transferAmount == null || !transferAmount.isPositive()) {
            throw new BalanceTransferException("INVALID_TRANSFER_AMOUNT", "Сумма трансфера должна быть положительной");
        }

        this.dualBalanceId = dualBalanceId;
        this.userId = userId;
        this.transactionId = TransactionId.generate();
        this.fromType = fromType;
        this.toType = toType;
        this.transferAmount = transferAmount;
        this.fromBalanceBefore = fromBalanceBefore;
        this.toBalanceBefore = toBalanceBefore;
        this.description = description;
        this.status = TransactionStatus.PENDING;
        this.createdAt = LocalDateTime.now();

        // Генерируем событие инициации трансфера
        registerEvent(new BalanceTransferInitiatedEvent(
                this, DualBalanceId.of(dualBalanceId), userId, fromType, toType,
                transferAmount, transactionId.getValue(), description, createdAt));
    }

    /**
     * Завершение трансфера
     */
    public void complete(Money fromBalanceAfter, Money toBalanceAfter) {
        validateCanChangeStatus();

        if (this.status == TransactionStatus.COMPLETED) {
            return; // Уже завершен
        }

        this.fromBalanceAfter = fromBalanceAfter;
        this.toBalanceAfter = toBalanceAfter;
        this.status = TransactionStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();

        // Генерируем событие завершения трансфера
        registerEvent(new BalanceTransferCompletedEvent(
                this, DualBalanceId.of(dualBalanceId), userId, fromType, toType,
                transferAmount, fromBalanceAfter, toBalanceAfter, transactionId.getValue()));
    }

    /**
     * Отмена трансфера
     */
    public void cancel(String reason) {
        validateCanChangeStatus();

        if (this.status == TransactionStatus.CANCELLED) {
            return; // Уже отменен
        }

        this.status = TransactionStatus.CANCELLED;
        this.completedAt = LocalDateTime.now();
        this.failureReason = reason;

        if (reason != null && !reason.trim().isEmpty()) {
            this.description = (this.description != null ? this.description + " " : "") +
                    "[ОТМЕНЕНО: " + reason + "]";
        }
    }

    /**
     * Пометка трансфера как неудачного
     */
    public void fail(String reason) {
        validateCanChangeStatus();

        if (this.status == TransactionStatus.FAILED) {
            return; // Уже помечен как неудачный
        }

        this.status = TransactionStatus.FAILED;
        this.completedAt = LocalDateTime.now();
        this.failureReason = reason;

        if (reason != null && !reason.trim().isEmpty()) {
            this.description = (this.description != null ? this.description + " " : "") +
                    "[ОШИБКА: " + reason + "]";
        }
    }

    /**
     * Проверка истечения времени ожидания трансфера
     */
    public boolean isTimedOut() {
        return isTimedOut(DEFAULT_TIMEOUT_MINUTES);
    }

    /**
     * Проверка истечения времени ожидания с заданным таймаутом
     */
    public boolean isTimedOut(int timeoutMinutes) {
        if (this.status != TransactionStatus.PENDING) {
            return false; // Только pending трансферы могут истечь
        }

        LocalDateTime timeoutThreshold = this.createdAt.plus(timeoutMinutes, ChronoUnit.MINUTES);
        return LocalDateTime.now().isAfter(timeoutThreshold);
    }

    /**
     * Автоматическая отмена по таймауту
     */
    public void timeoutCancel() {
        if (!isTimedOut()) {
            throw new BalanceTransferException("TRANSFER_NOT_TIMED_OUT", "Трансфер еще не истек");
        }

        cancel("Автоматическая отмена по таймауту");
    }

    /**
     * Проверка, можно ли откатить трансфер
     */
    public boolean canRollback() {
        return this.status == TransactionStatus.COMPLETED;
    }

    /**
     * Создание обратного трансфера (для отката)
     */
    public BalanceTransferAggregate createRollbackTransfer(String reason) {
        if (!canRollback()) {
            throw new BalanceTransferException("TRANSFER_CANNOT_ROLLBACK",
                    "Нельзя откатить трансфер в статусе " + this.status);
        }

        String rollbackDescription = String.format("Откат трансфера %s: %s",
                this.transactionId.getShortValue(), reason);

        return new BalanceTransferAggregate(
                this.dualBalanceId,
                this.userId,
                this.toType, // Меняем местами fromType и toType
                this.fromType,
                this.transferAmount,
                this.toBalanceAfter, // Меняем местами балансы
                this.fromBalanceAfter,
                rollbackDescription);
    }

    /**
     * Получение дельты изменения исходного баланса
     */
    public Money getFromBalanceDelta() {
        if (fromBalanceAfter == null || fromBalanceBefore == null) {
            return null;
        }
        return fromBalanceAfter.subtract(fromBalanceBefore);
    }

    /**
     * Получение дельты изменения целевого баланса
     */
    public Money getToBalanceDelta() {
        if (toBalanceAfter == null || toBalanceBefore == null) {
            return null;
        }
        return toBalanceAfter.subtract(toBalanceBefore);
    }

    /**
     * Получение времени выполнения трансфера
     */
    public Long getProcessingTimeSeconds() {
        if (completedAt == null) {
            return null;
        }
        return ChronoUnit.SECONDS.between(createdAt, completedAt);
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

    public boolean isBankToMainTransfer() {
        return fromType == BalanceType.BANK && toType == BalanceType.MAIN;
    }

    public boolean isMainToBankTransfer() {
        return fromType == BalanceType.MAIN && toType == BalanceType.BANK;
    }

    // Приватные методы

    private void validateCanChangeStatus() {
        if (this.status == TransactionStatus.COMPLETED) {
            throw new BalanceTransferException("TRANSFER_ALREADY_COMPLETED", "Трансфер уже завершен");
        }
        if (this.status == TransactionStatus.CANCELLED) {
            throw new BalanceTransferException("TRANSFER_ALREADY_CANCELLED", "Трансфер уже отменен");
        }
        if (this.status == TransactionStatus.FAILED) {
            throw new BalanceTransferException("TRANSFER_ALREADY_FAILED", "Трансфер уже помечен как неудачный");
        }
    }

    // Геттеры

    public Long getId() {
        return id;
    }

    public Long getDualBalanceId() {
        return dualBalanceId;
    }

    public Long getUserId() {
        return userId;
    }

    public TransactionId getTransactionId() {
        return transactionId;
    }

    public BalanceType getFromType() {
        return fromType;
    }

    public BalanceType getToType() {
        return toType;
    }

    public Money getTransferAmount() {
        return transferAmount;
    }

    public Money getFromBalanceBefore() {
        return fromBalanceBefore;
    }

    public Money getFromBalanceAfter() {
        return fromBalanceAfter;
    }

    public Money getToBalanceBefore() {
        return toBalanceBefore;
    }

    public Money getToBalanceAfter() {
        return toBalanceAfter;
    }

    public String getDescription() {
        return description;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public String getFailureReason() {
        return failureReason;
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
        BalanceTransferAggregate that = (BalanceTransferAggregate) o;
        return Objects.equals(id, that.id) && Objects.equals(transactionId, that.transactionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, transactionId);
    }

    @Override
    public String toString() {
        return String.format("BalanceTransferAggregate{id=%d, txId=%s, dualBalanceId=%d, %s->%s, amount=%s, status=%s}",
                id,
                transactionId != null ? transactionId.getShortValue() : "null",
                dualBalanceId, fromType.getCode(), toType.getCode(),
                transferAmount != null ? transferAmount.getFormattedAmount() : "null",
                status);
    }
}