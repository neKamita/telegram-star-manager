package shit.back.entity;

/**
 * Статусы платежа в системе Telegram Star Manager
 * 
 * Определяет жизненный цикл платежа от создания до завершения
 */
public enum PaymentStatus {

    /**
     * Платеж создан, но еще не обработан
     */
    PENDING("В ожидании", "⏳"),

    /**
     * Платеж успешно завершен
     */
    COMPLETED("Завершен", "✅"),

    /**
     * Платеж отклонен или произошла ошибка
     */
    FAILED("Не удался", "❌"),

    /**
     * Время платежа истекло
     */
    EXPIRED("Истек", "⌛"),

    /**
     * Платеж отменен пользователем или системой
     */
    CANCELLED("Отменен", "🚫"),

    /**
     * Платеж находится в процессе обработки
     */
    PROCESSING("Обрабатывается", "🔄"),

    /**
     * Требуется дополнительная верификация
     */
    VERIFICATION_REQUIRED("Требует верификации", "🔍"),

    /**
     * Возврат средств
     */
    REFUNDED("Возвращен", "↩️");

    private final String displayName;
    private final String emoji;

    PaymentStatus(String displayName, String emoji) {
        this.displayName = displayName;
        this.emoji = emoji;
    }

    /**
     * Получить отображаемое имя статуса
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Получить эмодзи статуса
     */
    public String getEmoji() {
        return emoji;
    }

    /**
     * Получить форматированный статус с эмодзи
     */
    public String getFormattedStatus() {
        return emoji + " " + displayName;
    }

    /**
     * Проверить, является ли статус финальным (не требует дальнейшей обработки)
     */
    public boolean isFinal() {
        return this == COMPLETED || this == FAILED || this == EXPIRED ||
                this == CANCELLED || this == REFUNDED;
    }

    /**
     * Проверить, является ли статус успешным
     */
    public boolean isSuccessful() {
        return this == COMPLETED;
    }

    /**
     * Проверить, можно ли отменить платеж в данном статусе
     */
    public boolean isCancellable() {
        return this == PENDING || this == PROCESSING || this == VERIFICATION_REQUIRED;
    }
}