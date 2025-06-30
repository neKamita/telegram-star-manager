package shit.back.domain.balance.valueobjects;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Objects;
import java.util.Set;

/**
 * Value Object для валют в системе Balance
 * 
 * Инкапсулирует логику работы с валютными кодами,
 * обеспечивает валидацию и неизменяемость.
 */
@Embeddable
public final class Currency {

    /**
     * Поддерживаемые валюты системы
     */
    private static final Set<String> SUPPORTED_CURRENCIES = Set.of(
            "USD", "EUR", "UAH", "KZT", "BYN", "UZS", "XTR");

    /**
     * Валюты, поддерживаемые Telegram Fragment
     */
    private static final Set<String> FRAGMENT_SUPPORTED_CURRENCIES = Set.of(
            "USD", "XTR");

    /**
     * Валюты, совместимые с Telegram Stars
     */
    private static final Set<String> STARS_COMPATIBLE_CURRENCIES = Set.of(
            "XTR", "USD");

    /**
     * Валюта по умолчанию
     */
    private static final String DEFAULT_CURRENCY = "USD";

    @NotBlank(message = "Код валюты не может быть пустым")
    @Size(min = 3, max = 3, message = "Код валюты должен состоять из 3 символов")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Код валюты должен содержать только заглавные латинские буквы")
    private final String code;

    /**
     * Приватный конструктор для создания экземпляра Currency
     */
    private Currency(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Код валюты не может быть пустым");
        }
        String normalizedCode = code.trim().toUpperCase();
        if (!normalizedCode.matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException("Неверный формат кода валюты: " + code);
        }
        if (!SUPPORTED_CURRENCIES.contains(normalizedCode)) {
            throw new IllegalArgumentException("Неподдерживаемая валюта: " + normalizedCode);
        }
        this.code = normalizedCode;
    }

    /**
     * Конструктор по умолчанию для JPA
     */
    protected Currency() {
        this.code = DEFAULT_CURRENCY;
    }

    /**
     * Factory method для создания Currency из строки
     */
    public static Currency of(String code) {
        return new Currency(code);
    }

    /**
     * Factory method для создания валюты по умолчанию
     */
    public static Currency defaultCurrency() {
        return new Currency(DEFAULT_CURRENCY);
    }

    /**
     * Factory method для создания USD
     */
    public static Currency usd() {
        return new Currency("USD");
    }

    /**
     * Factory method для создания EUR
     */
    public static Currency eur() {
        return new Currency("EUR");
    }

    /**
     * Factory method для создания XTR (Telegram Stars)
     */
    public static Currency xtr() {
        return new Currency("XTR");
    }

    /**
     * Factory method для создания UZS
     */
    public static Currency uzs() {
        return new Currency("UZS");
    }

    /**
     * Проверка поддержки валюты
     */
    public static boolean isSupported(String code) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }
        return SUPPORTED_CURRENCIES.contains(code.trim().toUpperCase());
    }

    /**
     * Получение всех поддерживаемых валют
     */
    public static Set<String> getSupportedCurrencies() {
        return Set.copyOf(SUPPORTED_CURRENCIES);
    }

    /**
     * Проверка, является ли валюта валютой по умолчанию
     */
    public boolean isDefault() {
        return DEFAULT_CURRENCY.equals(code);
    }

    /**
     * Проверка, является ли валюта долларом США
     */
    public boolean isUsd() {
        return "USD".equals(code);
    }

    /**
     * Проверка, является ли валюта евро
     */
    public boolean isEur() {
        return "EUR".equals(code);
    }

    /**
     * Проверка, является ли валюта Telegram Stars (XTR)
     */
    public boolean isXtr() {
        return "XTR".equals(code);
    }

    /**
     * Проверка, является ли валюта узбекским сумом
     */
    public boolean isUzs() {
        return "UZS".equals(code);
    }

    /**
     * Проверка поддержки валюты в Telegram Fragment
     */
    public boolean isFragmentSupported() {
        return FRAGMENT_SUPPORTED_CURRENCIES.contains(code);
    }

    /**
     * Проверка совместимости валюты с Telegram Stars
     */
    public boolean isStarsCompatible() {
        return STARS_COMPATIBLE_CURRENCIES.contains(code);
    }

    /**
     * Получение валют, поддерживаемых Fragment
     */
    public static Set<String> getFragmentSupportedCurrencies() {
        return Set.copyOf(FRAGMENT_SUPPORTED_CURRENCIES);
    }

    /**
     * Получение валют, совместимых с Stars
     */
    public static Set<String> getStarsCompatibleCurrencies() {
        return Set.copyOf(STARS_COMPATIBLE_CURRENCIES);
    }

    /**
     * Получение кода валюты
     */
    public String getCode() {
        return code;
    }

    /**
     * Получение символа валюты
     */
    public String getSymbol() {
        return switch (code) {
            case "USD" -> "$";
            case "EUR" -> "€";
            case "UAH" -> "₴";
            case "KZT" -> "₸";
            case "BYN" -> "Br";
            case "UZS" -> "сўм";
            case "XTR" -> "⭐";
            default -> code;
        };
    }

    /**
     * Получение названия валюты
     */
    public String getDisplayName() {
        return switch (code) {
            case "USD" -> "Доллар США";
            case "EUR" -> "Евро";
            case "UAH" -> "Украинская гривна";
            case "KZT" -> "Казахстанский тенге";
            case "BYN" -> "Белорусский рубль";
            case "UZS" -> "Узбекский сум";
            case "XTR" -> "Telegram Stars";
            default -> code;
        };
    }

    /**
     * Форматированное представление с символом
     */
    public String getFormattedName() {
        return String.format("%s (%s)", getDisplayName(), getSymbol());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Currency currency = (Currency) o;
        return Objects.equals(code, currency.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code);
    }

    @Override
    public String toString() {
        return code;
    }

    /**
     * Заглушка для совместимости с мапперами: Currency.fromCode(String)
     */
    public static Currency fromCode(String code) {
        return Currency.of(code);
    }
}