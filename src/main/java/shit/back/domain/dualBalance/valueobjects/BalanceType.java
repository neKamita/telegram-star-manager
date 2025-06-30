package shit.back.domain.dualBalance.valueobjects;

/**
 * Типы балансов в двухуровневой системе
 * 
 * BANK - баланс пользователей (получаемый от пополнений)
 * MAIN - корпоративный баланс (для покупки звезд через Fragment API)
 */
public enum BalanceType {

    /**
     * Банковский баланс пользователей
     * Пополняется через платежные системы
     */
    BANK("Банковский баланс", "bank", "💳"),

    /**
     * Основной корпоративный баланс
     * Используется для покупки звезд через Fragment API
     */
    MAIN("Основной баланс", "main", "🏦");

    private final String displayName;
    private final String code;
    private final String emoji;

    BalanceType(String displayName, String code, String emoji) {
        this.displayName = displayName;
        this.code = code;
        this.emoji = emoji;
    }

    /**
     * Получение отображаемого названия
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Получение кода типа баланса
     */
    public String getCode() {
        return code;
    }

    /**
     * Получение эмодзи для типа баланса
     */
    public String getEmoji() {
        return emoji;
    }

    /**
     * Получение полного названия с эмодзи
     */
    public String getFullName() {
        return emoji + " " + displayName;
    }

    /**
     * Проверка, является ли тип банковским балансом
     */
    public boolean isBank() {
        return this == BANK;
    }

    /**
     * Проверка, является ли тип основным балансом
     */
    public boolean isMain() {
        return this == MAIN;
    }

    /**
     * Factory method для создания типа из кода
     */
    public static BalanceType fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Код типа баланса не может быть пустым");
        }

        String normalizedCode = code.trim().toLowerCase();
        for (BalanceType type : values()) {
            if (type.code.equals(normalizedCode)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Неизвестный код типа баланса: " + code);
    }

    /**
     * Проверка валидности кода
     */
    public static boolean isValidCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }

        String normalizedCode = code.trim().toLowerCase();
        for (BalanceType type : values()) {
            if (type.code.equals(normalizedCode)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public String toString() {
        return getFullName();
    }
}