package shit.back.domain.balance;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.AbstractAggregateRoot;
import shit.back.domain.balance.events.BalanceChangedEvent;
import shit.back.domain.balance.exceptions.InsufficientFundsException;
import shit.back.domain.balance.exceptions.InvalidTransactionException;
import shit.back.domain.balance.valueobjects.BalanceId;
import shit.back.domain.balance.valueobjects.Currency;
import shit.back.domain.balance.valueobjects.Money;
import shit.back.entity.TransactionType;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Основной агрегат баланса пользователя в системе
 * 
 * Инкапсулирует бизнес-логику управления балансом,
 * обеспечивает инвариантность и генерирует доменные события.
 * Следует принципам DDD для управления lifecycle баланса.
 */
@Entity
@Table(name = "user_balances", indexes = {
        @Index(name = "idx_balance_user_id", columnList = "user_id", unique = true),
        @Index(name = "idx_balance_active", columnList = "is_active"),
        @Index(name = "idx_balance_currency", columnList = "currency"),
        @Index(name = "idx_balance_last_updated", columnList = "last_updated")
})
public class BalanceAggregate extends AbstractAggregateRoot<BalanceAggregate> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "current_balance", precision = 12, scale = 2))
    private Money currentBalance;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "total_deposited", precision = 12, scale = 2))
    private Money totalDeposited;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "total_spent", precision = 12, scale = 2))
    private Money totalSpent;

    @Embedded
    @AttributeOverride(name = "code", column = @Column(name = "currency", length = 3))
    private Currency currency;

    @NotNull
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Transient
    private BalancePolicy balancePolicy;

    /**
     * Конструктор по умолчанию для JPA
     */
    protected BalanceAggregate() {
    }

    /**
     * Конструктор для создания нового баланса
     */
    public BalanceAggregate(Long userId, Currency currency, BalancePolicy balancePolicy) {
        if (userId == null) {
            throw new InvalidTransactionException("USER_ID_REQUIRED", "null", "Положительное число");
        }
        if (currency == null) {
            currency = Currency.defaultCurrency();
        }
        if (balancePolicy == null) {
            throw new InvalidTransactionException("BALANCE_POLICY_REQUIRED", "null", "BalancePolicy instance");
        }

        this.userId = userId;
        this.currency = currency;
        this.currentBalance = Money.zero();
        this.totalDeposited = Money.zero();
        this.totalSpent = Money.zero();
        this.isActive = true;
        this.createdAt = LocalDateTime.now();
        this.lastUpdated = LocalDateTime.now();
        this.balancePolicy = balancePolicy;

        // Генерируем событие создания баланса
        registerEvent(new BalanceChangedEvent(
                this,
                userId,
                TransactionType.DEPOSIT,
                Money.zero().getAmount(),
                Money.zero().getAmount(),
                Money.zero().getAmount(),
                null,
                "Создание нового баланса",
                LocalDateTime.now(),
                null,
                null));
    }

    /**
     * Пополнение баланса
     */
    public void deposit(Money amount, String description, String transactionId) {
        validateActive();
        validateBalancePolicy();
        balancePolicy.validateDepositAmount(amount);

        Money balanceBefore = this.currentBalance;
        this.currentBalance = this.currentBalance.add(amount);
        this.totalDeposited = this.totalDeposited.add(amount);
        updateTimestamp();

        // Генерируем событие пополнения
        registerEvent(new BalanceChangedEvent(
                this,
                userId,
                TransactionType.DEPOSIT,
                amount.getAmount(),
                balanceBefore.getAmount(),
                this.currentBalance.getAmount(),
                transactionId,
                description != null ? description : "Пополнение баланса",
                LocalDateTime.now(),
                null,
                null));
    }

    /**
     * Списание с баланса
     */
    public void withdraw(Money amount, String description, String transactionId) {
        validateActive();
        validateBalancePolicy();
        balancePolicy.validateWithdrawalAmount(amount);

        if (!hasSufficientFunds(amount)) {
            throw new InsufficientFundsException(
                    BalanceId.of(this.id),
                    this.currentBalance,
                    amount);
        }

        Money balanceBefore = this.currentBalance;
        this.currentBalance = this.currentBalance.subtract(amount);
        this.totalSpent = this.totalSpent.add(amount);
        updateTimestamp();

        // Генерируем событие списания
        registerEvent(new BalanceChangedEvent(
                this,
                userId,
                TransactionType.WITHDRAWAL,
                amount.getAmount(),
                balanceBefore.getAmount(),
                this.currentBalance.getAmount(),
                transactionId,
                description != null ? description : "Списание с баланса",
                LocalDateTime.now(),
                null,
                null));
    }

    /**
     * Резервирование средств
     */
    public void reserve(Money amount, String orderId, String description, String transactionId) {
        validateActive();
        validateBalancePolicy();

        if (!hasSufficientFunds(amount)) {
            throw new InsufficientFundsException(
                    BalanceId.of(this.id),
                    this.currentBalance,
                    amount,
                    "Недостаточно средств для резервирования");
        }

        // Резервирование не изменяет текущий баланс, только создает pending транзакцию
        // Фактическое списание произойдет при подтверждении
        updateTimestamp();

        // Генерируем событие резервирования
        registerEvent(new BalanceChangedEvent(
                this,
                userId,
                TransactionType.PURCHASE,
                amount.getAmount(),
                this.currentBalance.getAmount(),
                this.currentBalance.getAmount(), // Баланс пока не изменился
                transactionId,
                description != null ? description : "Резервирование средств",
                LocalDateTime.now(),
                orderId,
                null));
    }

    /**
     * Освобождение зарезервированных средств
     */
    public void release(Money amount, String orderId, String description, String transactionId) {
        validateActive();
        updateTimestamp();

        // Генерируем событие освобождения
        registerEvent(new BalanceChangedEvent(
                this,
                userId,
                TransactionType.REFUND,
                amount.getAmount(),
                this.currentBalance.getAmount(),
                this.currentBalance.getAmount(), // Баланс не изменяется при освобождении резерва
                transactionId,
                description != null ? description : "Освобождение резерва",
                LocalDateTime.now(),
                orderId,
                null));
    }

    /**
     * Возврат средств на баланс
     */
    public void refund(Money amount, String description, String transactionId, String orderId) {
        validateActive();
        validateBalancePolicy();
        balancePolicy.validateDepositAmount(amount);

        Money balanceBefore = this.currentBalance;
        this.currentBalance = this.currentBalance.add(amount);

        // При возврате уменьшаем totalSpent если это возможно
        if (this.totalSpent.isGreaterThanOrEqual(amount)) {
            this.totalSpent = this.totalSpent.subtract(amount);
        }

        updateTimestamp();

        // Генерируем событие возврата
        registerEvent(new BalanceChangedEvent(
                this,
                userId,
                TransactionType.REFUND,
                amount.getAmount(),
                balanceBefore.getAmount(),
                this.currentBalance.getAmount(),
                transactionId,
                description != null ? description : "Возврат средств",
                LocalDateTime.now(),
                orderId,
                null));
    }

    /**
     * Административная корректировка баланса
     */
    public void adjustBalance(Money amount, String reason, String adminUser, String transactionId) {
        validateActive();
        validateBalancePolicy();
        balancePolicy.validateAdminOperation(adminUser, "BALANCE_ADJUSTMENT");

        Money balanceBefore = this.currentBalance;
        Money newBalance = this.currentBalance.add(amount);

        if (newBalance.getAmount().compareTo(Money.zero().getAmount()) < 0) {
            throw new InvalidTransactionException("NEGATIVE_BALANCE_NOT_ALLOWED",
                    newBalance.getFormattedAmount(), "Положительный баланс");
        }

        this.currentBalance = newBalance;
        updateTimestamp();
        addNote(String.format("Админская корректировка: %s (%s) - %s",
                amount.getFormattedAmount(), reason, LocalDateTime.now()));

        // Генерируем событие корректировки
        registerEvent(new BalanceChangedEvent(
                this,
                userId,
                TransactionType.ADJUSTMENT,
                amount.getAmount().abs(),
                balanceBefore.getAmount(),
                this.currentBalance.getAmount(),
                transactionId,
                String.format("Админская корректировка: %s (Админ: %s)", reason, adminUser),
                LocalDateTime.now(),
                null,
                null));
    }

    /**
     * Проверка достаточности средств
     */
    public boolean hasSufficientFunds(Money amount) {
        return amount != null && this.currentBalance.isGreaterThanOrEqual(amount);
    }

    /**
     * Деактивация баланса
     */
    public void deactivate(String reason, String adminUser) {
        if (!this.isActive) {
            return; // Уже деактивирован
        }

        this.isActive = false;
        updateTimestamp();
        addNote(String.format("Деактивировано: %s (Админ: %s) - %s",
                reason, adminUser, LocalDateTime.now()));
    }

    /**
     * Активация баланса
     */
    public void activate(String reason, String adminUser) {
        if (this.isActive) {
            return; // Уже активен
        }

        this.isActive = true;
        updateTimestamp();
        addNote(String.format("Активировано: %s (Админ: %s) - %s",
                reason, adminUser, LocalDateTime.now()));
    }

    /**
     * Получение общего оборота
     */
    public Money getTotalTurnover() {
        return totalDeposited.add(totalSpent);
    }

    // Приватные методы для валидации и обновления

    private void validateActive() {
        if (!this.isActive) {
            throw new InvalidTransactionException("BALANCE_INACTIVE",
                    "false", "true");
        }
    }

    private void validateBalancePolicy() {
        if (this.balancePolicy == null) {
            throw new InvalidTransactionException("BALANCE_POLICY_NOT_SET",
                    "null", "BalancePolicy instance");
        }
    }

    private void updateTimestamp() {
        this.lastUpdated = LocalDateTime.now();
    }

    private void addNote(String note) {
        if (this.notes == null) {
            this.notes = note;
        } else {
            this.notes = this.notes + "\n" + note;
        }
    }

    // Геттеры

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Money getCurrentBalance() {
        return currentBalance;
    }

    public Money getTotalDeposited() {
        return totalDeposited;
    }

    public Money getTotalSpent() {
        return totalSpent;
    }

    public Currency getCurrency() {
        return currency;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getVersion() {
        return version;
    }

    public String getNotes() {
        return notes;
    }

    public void setBalancePolicy(BalancePolicy balancePolicy) {
        this.balancePolicy = balancePolicy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        BalanceAggregate that = (BalanceAggregate) o;
        return Objects.equals(id, that.id) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, userId);
    }

    @Override
    public String toString() {
        return String.format("BalanceAggregate{id=%d, userId=%d, balance=%s, currency=%s, active=%s}",
                id, userId,
                currentBalance != null ? currentBalance.getFormattedAmount() : "null",
                currency != null ? currency.getCode() : "null",
                isActive);
    }

    /**
     * Публичный конструктор для совместимости с мапперами и тестами.
     */
    public BalanceAggregate(
            BalanceId balanceId,
            Long userId,
            Currency currency,
            Money currentBalance,
            Money totalDeposited,
            Money totalSpent,
            Boolean isActive,
            LocalDateTime createdAt,
            LocalDateTime lastUpdated) {
        this.id = balanceId != null ? balanceId.getValue() : null;
        this.userId = userId;
        this.currency = currency;
        this.currentBalance = currentBalance;
        this.totalDeposited = totalDeposited;
        this.totalSpent = totalSpent;
        this.isActive = isActive != null ? isActive : true;
        this.createdAt = createdAt;
        this.lastUpdated = lastUpdated;
    }

    /**
     * Публичный сеттер для notes (для совместимости с мапперами).
     */
    public void setNotes(String notes) {
        this.notes = notes;
    }

    /**
     * Публичный геттер isActive (для совместимости с мапперами).
     */
    public boolean isActive() {
        return Boolean.TRUE.equals(this.isActive);
    }
}