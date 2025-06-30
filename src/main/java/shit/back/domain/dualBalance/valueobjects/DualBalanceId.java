package shit.back.domain.dualBalance.valueobjects;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.Objects;

/**
 * Value Object для идентификаторов двойного баланса
 * 
 * Инкапсулирует логику работы с ID двойных балансов,
 * обеспечивает type safety и валидацию.
 */
@Embeddable
public final class DualBalanceId {

    @NotNull(message = "ID двойного баланса не может быть null")
    @Positive(message = "ID двойного баланса должен быть положительным")
    private final Long value;

    /**
     * Приватный конструктор для создания экземпляра DualBalanceId
     */
    private DualBalanceId(Long value) {
        if (value == null) {
            throw new IllegalArgumentException("ID двойного баланса не может быть null");
        }
        if (value <= 0) {
            throw new IllegalArgumentException("ID двойного баланса должен быть положительным");
        }
        this.value = value;
    }

    /**
     * Конструктор по умолчанию для JPA
     */
    protected DualBalanceId() {
        this.value = null;
    }

    /**
     * Factory method для создания DualBalanceId из Long
     */
    public static DualBalanceId of(Long value) {
        return new DualBalanceId(value);
    }

    /**
     * Factory method для создания DualBalanceId из long (примитив)
     */
    public static DualBalanceId of(long value) {
        return new DualBalanceId(value);
    }

    /**
     * Factory method для создания DualBalanceId из строки
     */
    public static DualBalanceId of(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("ID двойного баланса не может быть пустым");
        }
        try {
            Long longValue = Long.parseLong(value.trim());
            return new DualBalanceId(longValue);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Неверный формат ID двойного баланса: " + value, e);
        }
    }

    /**
     * Проверка валидности Long как потенциального DualBalanceId
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

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        DualBalanceId that = (DualBalanceId) o;
        return Objects.equals(value, that.value);
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