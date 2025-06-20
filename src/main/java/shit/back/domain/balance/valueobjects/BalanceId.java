package shit.back.domain.balance.valueobjects;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.Objects;

/**
 * Value Object для идентификаторов баланса в системе Balance
 * 
 * Инкапсулирует логику работы с ID балансов,
 * обеспечивает type safety и валидацию.
 */
@Embeddable
public final class BalanceId {

    /**
     * Публичный конструктор для совместимости с мапперами и тестами
     */
    public BalanceId(Long value) {
        if (value == null) {
            throw new IllegalArgumentException("ID баланса не может быть null");
        }
        if (value <= 0) {
            throw new IllegalArgumentException("ID баланса должен быть положительным");
        }
        this.value = value;
    }

    public BalanceId(long value) {
        if (value <= 0) {
            throw new IllegalArgumentException("ID баланса должен быть положительным");
        }
        this.value = value;
    }

    @NotNull(message = "ID баланса не может быть null")
    @Positive(message = "ID баланса должен быть положительным")
    private final Long value;

    /**
     * Конструктор по умолчанию для JPA
     */
    protected BalanceId() {
        this.value = null;
    }

    /**
     * Factory method для создания BalanceId из Long
     */
    public static BalanceId of(Long value) {
        return new BalanceId(value);
    }

    /**
     * Factory method для создания BalanceId из long (примитив)
     */
    public static BalanceId of(long value) {
        return new BalanceId(value);
    }

    /**
     * Factory method для создания BalanceId из строки
     */
    public static BalanceId of(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("ID баланса не может быть пустым");
        }
        try {
            Long longValue = Long.parseLong(value.trim());
            return new BalanceId(longValue);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Неверный формат ID баланса: " + value, e);
        }
    }

    /**
     * Проверка валидности строки как потенциального BalanceId
     */
    public static boolean isValid(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        try {
            Long longValue = Long.parseLong(value.trim());
            return longValue > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Проверка валидности Long как потенциального BalanceId
     */
    public static boolean isValid(Long value) {
        return value != null && value > 0;
    }

    /**
     * Получение значения ID как Long
     */
    public Long getValue() {
        return value;
    }

    /**
     * Получение значения ID как long (примитив)
     */
    public long longValue() {
        return value;
    }

    /**
     * Получение строкового представления ID
     */
    public String getStringValue() {
        return value.toString();
    }

    /**
     * Проверка на равенство с другим BalanceId
     */
    public boolean equals(BalanceId other) {
        return other != null && Objects.equals(this.value, other.value);
    }

    /**
     * Проверка на равенство с Long значением
     */
    public boolean equalsLong(Long other) {
        return Objects.equals(this.value, other);
    }

    /**
     * Проверка на равенство со строковым представлением
     */
    public boolean equalsString(String other) {
        if (other == null || other.trim().isEmpty()) {
            return false;
        }
        try {
            Long otherValue = Long.parseLong(other.trim());
            return Objects.equals(this.value, otherValue);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Сравнение с другим BalanceId
     */
    public int compareTo(BalanceId other) {
        if (other == null) {
            throw new IllegalArgumentException("Сравниваемый ID не может быть null");
        }
        return this.value.compareTo(other.value);
    }

    /**
     * Проверка, больше ли текущий ID чем другой
     */
    public boolean isGreaterThan(BalanceId other) {
        return compareTo(other) > 0;
    }

    /**
     * Проверка, меньше ли текущий ID чем другой
     */
    public boolean isLessThan(BalanceId other) {
        return compareTo(other) < 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        BalanceId balanceId = (BalanceId) o;
        return Objects.equals(value, balanceId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value != null ? value.toString() : "null";
    }
}