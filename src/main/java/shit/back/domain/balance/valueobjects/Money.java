package shit.back.domain.balance.valueobjects;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Value Object для денежных сумм в системе Balance
 * 
 * Инкапсулирует бизнес-логику работы с денежными суммами,
 * обеспечивает неизменяемость и валидацию на доменном уровне.
 */
@Embeddable
public final class Money {

    /**
     * Публичный конструктор для совместимости с мапперами: Money(BigDecimal,
     * Currency)
     */
    public Money(BigDecimal amount, Currency currency) {
        this(amount);
    }

    private static final int SCALE = 2; // Количество знаков после запятой
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    @NotNull(message = "Сумма не может быть null")
    @DecimalMin(value = "0.00", message = "Сумма не может быть отрицательной")
    @Digits(integer = 10, fraction = 2, message = "Неверный формат суммы")
    private final BigDecimal amount;

    /**
     * Приватный конструктор для создания экземпляра Money
     */
    private Money(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Сумма не может быть null");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Сумма не может быть отрицательной");
        }
        this.amount = amount.setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * Конструктор по умолчанию для JPA
     */
    protected Money() {
        this.amount = BigDecimal.ZERO;
    }

    /**
     * Factory method для создания Money из BigDecimal
     */
    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    /**
     * Factory method для создания Money из double
     */
    public static Money of(double amount) {
        return new Money(BigDecimal.valueOf(amount));
    }

    /**
     * Factory method для создания Money из строки
     */
    public static Money of(String amount) {
        try {
            return new Money(new BigDecimal(amount));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Неверный формат суммы: " + amount, e);
        }
    }

    /**
     * Factory method для создания нулевой суммы
     */
    public static Money zero() {
        return new Money(BigDecimal.ZERO);
    }

    /**
     * Проверка на нулевую сумму
     */
    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * Проверка на положительную сумму
     */
    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Сложение денежных сумм
     */
    public Money add(Money other) {
        if (other == null) {
            throw new IllegalArgumentException("Слагаемое не может быть null");
        }
        return new Money(this.amount.add(other.amount));
    }

    /**
     * Вычитание денежных сумм
     */
    public Money subtract(Money other) {
        if (other == null) {
            throw new IllegalArgumentException("Вычитаемое не может быть null");
        }
        BigDecimal result = this.amount.subtract(other.amount);
        if (result.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Результат вычитания не может быть отрицательным");
        }
        return new Money(result);
    }

    /**
     * Умножение на коэффициент
     */
    public Money multiply(BigDecimal multiplier) {
        if (multiplier == null) {
            throw new IllegalArgumentException("Множитель не может быть null");
        }
        if (multiplier.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Множитель не может быть отрицательным");
        }
        return new Money(this.amount.multiply(multiplier));
    }

    /**
     * Сравнение денежных сумм
     */
    public int compareTo(Money other) {
        if (other == null) {
            throw new IllegalArgumentException("Сравниваемая сумма не может быть null");
        }
        return this.amount.compareTo(other.amount);
    }

    /**
     * Проверка достаточности суммы
     */
    public boolean isGreaterThanOrEqual(Money other) {
        return compareTo(other) >= 0;
    }

    /**
     * Проверка превышения суммы
     */
    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    /**
     * Проверка меньшей суммы
     */
    public boolean isLessThan(Money other) {
        return compareTo(other) < 0;
    }

    /**
     * Получение значения как BigDecimal
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * Форматированное представление суммы
     */
    public String getFormattedAmount() {
        return String.format("%.2f", amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Money money = (Money) o;
        return Objects.equals(amount, money.amount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount);
    }

    @Override
    public String toString() {
        return getFormattedAmount();
    }
}