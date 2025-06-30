package shit.back.domain.dualBalance;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.AbstractAggregateRoot;
import shit.back.domain.balance.valueobjects.Currency;
import shit.back.domain.balance.valueobjects.Money;
import shit.back.domain.dualBalance.events.BalanceTransferCompletedEvent;
import shit.back.domain.dualBalance.events.BalanceTransferInitiatedEvent;
import shit.back.domain.dualBalance.exceptions.BalanceTransferException;
import shit.back.domain.dualBalance.valueobjects.BalanceType;
import shit.back.domain.dualBalance.valueobjects.DualBalanceId;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Агрегат двухуровневой системы балансов
 * 
 * Управляет двумя типами балансов: bankBalance (от пользователей) и mainBalance
 * (корпоративный).
 * Обеспечивает трансферы между балансами, валидацию операций и генерацию
 * доменных событий.
 * Следует принципам DDD для управления lifecycle двойного баланса.
 */
@Entity
@Table(name = "dual_balances", indexes = {
        @Index(name = "idx_dual_balance_user_id", columnList = "user_id", unique = true),
        @Index(name = "idx_dual_balance_active", columnList = "is_active"),
        @Index(name = "idx_dual_balance_currency", columnList = "currency"),
        @Index(name = "idx_dual_balance_last_updated", columnList = "last_updated")
})
public class DualBalanceAggregate extends AbstractAggregateRoot<DualBalanceAggregate> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "bank_balance", precision = 12, scale = 2))
    private Money bankBalance;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "main_balance", precision = 12, scale = 2))
    private Money mainBalance;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "total_transferred_to_main", precision = 12, scale = 2))
    private Money totalTransferredToMain;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "total_spent_from_main", precision = 12, scale = 2))
    private Money totalSpentFromMain;

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

    /**
     * Конструктор по умолчанию для JPA
     */
    protected DualBalanceAggregate() {
    }

    /**
     * Конструктор для создания нового двойного баланса
     */
    public DualBalanceAggregate(Long userId, Currency currency) {
        if (userId == null) {
            throw new BalanceTransferException("USER_ID_REQUIRED", "ID пользователя не может быть null");
        }
        if (currency == null) {
            currency = Currency.defaultCurrency();
        }

        this.userId = userId;
        this.currency = currency;
        this.bankBalance = Money.zero();
        this.mainBalance = Money.zero();
        this.totalTransferredToMain = Money.zero();
        this.totalSpentFromMain = Money.zero();
        this.isActive = true;
        this.createdAt = LocalDateTime.now();
        this.lastUpdated = LocalDateTime.now();

        addNote("Создан двойной баланс для пользователя " + userId);
    }

    /**
     * Пополнение bank баланса (от пользователей)
     */
    public void depositToBankBalance(Money amount, String description) {
        validateActive();
        validateAmount(amount);

        this.bankBalance = this.bankBalance.add(amount);
        updateTimestamp();
        addNote(String.format("Пополнение bank баланса: %s - %s", amount.getFormattedAmount(), description));
    }

    /**
     * Трансфер из bank баланса в main баланс
     */
    public void transferBankToMain(Money amount, String description) {
        validateActive();
        validateAmount(amount);

        if (!hasSufficientBankFunds(amount)) {
            throw new BalanceTransferException(
                    DualBalanceId.of(this.id), BalanceType.BANK, BalanceType.MAIN,
                    amount, this.bankBalance, "INSUFFICIENT_BANK_FUNDS");
        }

        String transferId = UUID.randomUUID().toString();

        // Генерируем событие инициации трансфера
        registerEvent(new BalanceTransferInitiatedEvent(
                this, DualBalanceId.of(this.id), userId,
                BalanceType.BANK, BalanceType.MAIN, amount, transferId));

        this.bankBalance = this.bankBalance.subtract(amount);
        this.mainBalance = this.mainBalance.add(amount);
        this.totalTransferredToMain = this.totalTransferredToMain.add(amount);
        updateTimestamp();

        addNote(String.format("Трансфер Bank->Main: %s - %s (ID: %s)",
                amount.getFormattedAmount(), description, transferId.substring(0, 8)));

        // Генерируем событие завершения трансфера
        registerEvent(new BalanceTransferCompletedEvent(
                this, DualBalanceId.of(this.id), userId,
                BalanceType.BANK, BalanceType.MAIN, amount,
                this.bankBalance, this.mainBalance, transferId));
    }

    /**
     * Списание с main баланса (для покупки звезд)
     */
    public void withdrawFromMainBalance(Money amount, String description, String purchaseId) {
        validateActive();
        validateAmount(amount);

        if (!hasSufficientMainFunds(amount)) {
            throw new BalanceTransferException(
                    DualBalanceId.of(this.id), BalanceType.MAIN, null,
                    amount, this.mainBalance, "INSUFFICIENT_MAIN_FUNDS");
        }

        this.mainBalance = this.mainBalance.subtract(amount);
        this.totalSpentFromMain = this.totalSpentFromMain.add(amount);
        updateTimestamp();

        addNote(String.format("Списание с Main баланса: %s - %s (Покупка: %s)",
                amount.getFormattedAmount(), description, purchaseId != null ? purchaseId.substring(0, 8) : "N/A"));
    }

    /**
     * Возврат средств на main баланс (при неудачной покупке звезд)
     */
    public void refundToMainBalance(Money amount, String description, String purchaseId) {
        validateActive();
        validateAmount(amount);

        this.mainBalance = this.mainBalance.add(amount);

        // При возврате уменьшаем totalSpentFromMain если это возможно
        if (this.totalSpentFromMain.isGreaterThanOrEqual(amount)) {
            this.totalSpentFromMain = this.totalSpentFromMain.subtract(amount);
        }

        updateTimestamp();

        addNote(String.format("Возврат на Main баланс: %s - %s (Покупка: %s)",
                amount.getFormattedAmount(), description, purchaseId != null ? purchaseId.substring(0, 8) : "N/A"));
    }

    /**
     * Проверка достаточности средств на bank балансе
     */
    public boolean hasSufficientBankFunds(Money amount) {
        return amount != null && this.bankBalance.isGreaterThanOrEqual(amount);
    }

    /**
     * Проверка достаточности средств на main балансе
     */
    public boolean hasSufficientMainFunds(Money amount) {
        return amount != null && this.mainBalance.isGreaterThanOrEqual(amount);
    }

    /**
     * Получение общего баланса (bank + main)
     */
    public Money getTotalBalance() {
        return bankBalance.add(mainBalance);
    }

    /**
     * Получение коэффициента использования main баланса
     */
    public double getMainBalanceUtilizationRatio() {
        if (totalTransferredToMain.isZero()) {
            return 0.0;
        }
        return totalSpentFromMain.getAmount().doubleValue() / totalTransferredToMain.getAmount().doubleValue();
    }

    /**
     * Деактивация двойного баланса
     */
    public void deactivate(String reason, String adminUser) {
        if (!this.isActive) {
            return; // Уже деактивирован
        }

        this.isActive = false;
        updateTimestamp();
        addNote(String.format("Деактивировано: %s (Админ: %s) - %s", reason, adminUser, LocalDateTime.now()));
    }

    /**
     * Активация двойного баланса
     */
    public void activate(String reason, String adminUser) {
        if (this.isActive) {
            return; // Уже активен
        }

        this.isActive = true;
        updateTimestamp();
        addNote(String.format("Активировано: %s (Админ: %s) - %s", reason, adminUser, LocalDateTime.now()));
    }

    // Приватные методы для валидации и обновления

    private void validateActive() {
        if (!this.isActive) {
            throw new BalanceTransferException("DUAL_BALANCE_INACTIVE", "Двойной баланс неактивен");
        }
    }

    private void validateAmount(Money amount) {
        if (amount == null) {
            throw new BalanceTransferException("AMOUNT_REQUIRED", "Сумма не может быть null");
        }
        if (!amount.isPositive()) {
            throw new BalanceTransferException("AMOUNT_MUST_BE_POSITIVE", "Сумма должна быть положительной");
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

    public Money getBankBalance() {
        return bankBalance;
    }

    public Money getMainBalance() {
        return mainBalance;
    }

    public Money getTotalTransferredToMain() {
        return totalTransferredToMain;
    }

    public Money getTotalSpentFromMain() {
        return totalSpentFromMain;
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

    public boolean isActive() {
        return Boolean.TRUE.equals(this.isActive);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        DualBalanceAggregate that = (DualBalanceAggregate) o;
        return Objects.equals(id, that.id) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, userId);
    }

    @Override
    public String toString() {
        return String.format("DualBalanceAggregate{id=%d, userId=%d, bank=%s, main=%s, currency=%s, active=%s}",
                id, userId,
                bankBalance != null ? bankBalance.getFormattedAmount() : "null",
                mainBalance != null ? mainBalance.getFormattedAmount() : "null",
                currency != null ? currency.getCode() : "null",
                isActive);
    }
}