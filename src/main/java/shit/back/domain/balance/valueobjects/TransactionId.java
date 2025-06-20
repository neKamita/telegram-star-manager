package shit.back.domain.balance.valueobjects;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Value Object для идентификаторов транзакций в системе Balance
 * 
 * Инкапсулирует логику генерации и валидации ID транзакций,
 * обеспечивает неизменяемость и type safety.
 */
@Embeddable
public final class TransactionId {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    @NotBlank(message = "ID транзакции не может быть пустым")
    @Size(min = 36, max = 36, message = "ID транзакции должен быть длиной 36 символов")
    private final String value;

    /**
     * Приватный конструктор для создания экземпляра TransactionId
     */
    private TransactionId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("ID транзакции не может быть пустым");
        }
        String normalizedValue = value.trim().toLowerCase();
        if (!isValidUuidFormat(normalizedValue)) {
            throw new IllegalArgumentException("Неверный формат ID транзакции: " + value);
        }
        this.value = normalizedValue;
    }

    /**
     * Конструктор по умолчанию для JPA
     */
    protected TransactionId() {
        this.value = UUID.randomUUID().toString();
    }

    /**
     * Factory method для создания TransactionId из строки
     */
    public static TransactionId of(String value) {
        return new TransactionId(value);
    }

    /**
     * Factory method для генерации нового уникального TransactionId
     */
    public static TransactionId generate() {
        return new TransactionId(UUID.randomUUID().toString());
    }

    /**
     * Factory method для создания TransactionId из UUID
     */
    public static TransactionId of(UUID uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("UUID не может быть null");
        }
        return new TransactionId(uuid.toString());
    }

    /**
     * Проверка валидности формата UUID
     */
    private static boolean isValidUuidFormat(String value) {
        return value != null && UUID_PATTERN.matcher(value).matches();
    }

    /**
     * Проверка валидности строки как потенциального TransactionId
     */
    public static boolean isValid(String value) {
        return value != null && !value.trim().isEmpty() && isValidUuidFormat(value.trim().toLowerCase());
    }

    /**
     * Получение значения ID как строки
     */
    public String getValue() {
        return value;
    }

    /**
     * Получение значения ID как UUID
     */
    public UUID toUuid() {
        return UUID.fromString(value);
    }

    /**
     * Получение короткой версии ID для логов (первые 8 символов)
     */
    public String getShortValue() {
        return value.length() >= 8 ? value.substring(0, 8) : value;
    }

    /**
     * Получение форматированного представления для отображения
     */
    public String getDisplayValue() {
        return value.toUpperCase();
    }

    /**
     * Проверка на равенство с другим TransactionId
     */
    public boolean equals(TransactionId other) {
        return other != null && Objects.equals(this.value, other.value);
    }

    /**
     * Проверка на равенство со строковым представлением
     */
    public boolean equalsString(String other) {
        return other != null && Objects.equals(this.value, other.trim().toLowerCase());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TransactionId that = (TransactionId) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}