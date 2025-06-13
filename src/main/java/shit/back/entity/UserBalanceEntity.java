package shit.back.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Сущность баланса пользователя в системе Telegram Star Manager
 * 
 * Хранит информацию о текущем балансе пользователя, общей сумме пополнений
 * и трат, а также метаданные для администрирования.
 */
@Entity
@Table(name = "user_balances", indexes = {
        @Index(name = "idx_user_balances_user_id", columnList = "user_id", unique = true),
        @Index(name = "idx_user_balances_active", columnList = "is_active"),
        @Index(name = "idx_user_balances_currency", columnList = "currency"),
        @Index(name = "idx_user_balances_last_updated", columnList = "last_updated")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_balances_user_id", columnNames = "user_id")
})
@Data
@NoArgsConstructor
public class UserBalanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * ID пользователя - внешний ключ к UserSessionEntity
     * Уникальное поле - один баланс на пользователя
     */
    @Column(name = "user_id", nullable = false, unique = true)
    @NotNull(message = "ID пользователя не может быть пустым")
    private Long userId;

    /**
     * Текущий баланс пользователя
     * Не может быть отрицательным (проверка на уровне приложения)
     */
    @Column(name = "current_balance", precision = 12, scale = 2, nullable = false)
    @NotNull(message = "Текущий баланс не может быть пустым")
    @DecimalMin(value = "0.00", message = "Баланс не может быть отрицательным")
    @Digits(integer = 10, fraction = 2, message = "Неверный формат баланса")
    private BigDecimal currentBalance = BigDecimal.ZERO;

    /**
     * Общая сумма пополнений за все время
     */
    @Column(name = "total_deposited", precision = 12, scale = 2, nullable = false)
    @NotNull(message = "Общая сумма пополнений не может быть пустой")
    @DecimalMin(value = "0.00", message = "Сумма пополнений не может быть отрицательной")
    @Digits(integer = 10, fraction = 2, message = "Неверный формат суммы пополнений")
    private BigDecimal totalDeposited = BigDecimal.ZERO;

    /**
     * Общая сумма трат за все время
     */
    @Column(name = "total_spent", precision = 12, scale = 2, nullable = false)
    @NotNull(message = "Общая сумма трат не может быть пустой")
    @DecimalMin(value = "0.00", message = "Сумма трат не может быть отрицательной")
    @Digits(integer = 10, fraction = 2, message = "Неверный формат суммы трат")
    private BigDecimal totalSpent = BigDecimal.ZERO;

    /**
     * Валюта баланса (по умолчанию USD)
     */
    @Column(name = "currency", length = 3, nullable = false)
    @NotBlank(message = "Валюта не может быть пустой")
    @Size(min = 3, max = 3, message = "Код валюты должен состоять из 3 символов")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Код валюты должен содержать только заглавные латинские буквы")
    private String currency = "USD";

    /**
     * Активность баланса (для soft delete)
     */
    @Column(name = "is_active", nullable = false)
    @NotNull(message = "Статус активности не может быть пустым")
    private Boolean isActive = true;

    /**
     * Время последнего обновления баланса
     */
    @UpdateTimestamp
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    /**
     * Время создания записи баланса
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Заметки администратора для данного баланса
     */
    @Column(name = "notes", columnDefinition = "TEXT")
    @Size(max = 1000, message = "Заметки не могут превышать 1000 символов")
    private String notes;

    /**
     * Конструктор для создания нового баланса пользователя
     */
    public UserBalanceEntity(Long userId) {
        this.userId = userId;
        this.currentBalance = BigDecimal.ZERO;
        this.totalDeposited = BigDecimal.ZERO;
        this.totalSpent = BigDecimal.ZERO;
        this.currency = "USD";
        this.isActive = true;
    }

    /**
     * Конструктор с указанием валюты
     */
    public UserBalanceEntity(Long userId, String currency) {
        this(userId);
        this.currency = currency != null ? currency : "USD";
    }

    /**
     * Пополнение баланса
     * 
     * @param amount сумма пополнения (должна быть положительной)
     */
    public void deposit(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Сумма пополнения должна быть положительной");
        }
        this.currentBalance = this.currentBalance.add(amount);
        this.totalDeposited = this.totalDeposited.add(amount);
    }

    /**
     * Списание с баланса (для покупок)
     * 
     * @param amount сумма списания (должна быть положительной)
     * @return true если операция успешна, false если недостаточно средств
     */
    public boolean withdraw(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Сумма списания должна быть положительной");
        }

        if (this.currentBalance.compareTo(amount) < 0) {
            return false; // Недостаточно средств
        }

        this.currentBalance = this.currentBalance.subtract(amount);
        this.totalSpent = this.totalSpent.add(amount);
        return true;
    }

    /**
     * Возврат средств на баланс
     * 
     * @param amount сумма возврата (должна быть положительной)
     */
    public void refund(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Сумма возврата должна быть положительной");
        }
        this.currentBalance = this.currentBalance.add(amount);
        // totalSpent уменьшается только если это не превышает текущую сумму трат
        if (this.totalSpent.compareTo(amount) >= 0) {
            this.totalSpent = this.totalSpent.subtract(amount);
        }
    }

    /**
     * Административная корректировка баланса
     * 
     * @param amount сумма корректировки (может быть отрицательной)
     * @param reason причина корректировки
     */
    public void adjustBalance(BigDecimal amount, String reason) {
        if (amount == null) {
            throw new IllegalArgumentException("Сумма корректировки не может быть null");
        }

        BigDecimal newBalance = this.currentBalance.add(amount);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Корректировка приведет к отрицательному балансу");
        }

        this.currentBalance = newBalance;
        this.notes = (this.notes != null ? this.notes + "\n" : "") +
                "Корректировка: " + amount + " (" + reason + ") - " + LocalDateTime.now();
    }

    /**
     * Проверка достаточности средств
     * 
     * @param amount требуемая сумма
     * @return true если средств достаточно
     */
    public boolean hasSufficientFunds(BigDecimal amount) {
        return amount != null && this.currentBalance.compareTo(amount) >= 0;
    }

    /**
     * Получение форматированного баланса с символом валюты
     */
    public String getFormattedBalance() {
        return String.format("%.2f %s", currentBalance, currency);
    }

    /**
     * Получение статистики оборота (общая сумма операций)
     */
    public BigDecimal getTotalTurnover() {
        return totalDeposited.add(totalSpent);
    }

    /**
     * Деактивация баланса (soft delete)
     */
    public void deactivate() {
        this.isActive = false;
        this.notes = (this.notes != null ? this.notes + "\n" : "") +
                "Деактивировано: " + LocalDateTime.now();
    }

    /**
     * Активация баланса
     */
    public void activate() {
        this.isActive = true;
        this.notes = (this.notes != null ? this.notes + "\n" : "") +
                "Активировано: " + LocalDateTime.now();
    }
}