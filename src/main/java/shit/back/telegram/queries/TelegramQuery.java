package shit.back.telegram.queries;

/**
 * Базовый интерфейс для всех Telegram запросов (CQRS Read Side)
 * 
 * Унифицированный интерфейс запросов для новой функциональной архитектуры
 */
public interface TelegramQuery {

    /**
     * Получить ID пользователя, выполняющего запрос
     */
    Long getUserId();

    /**
     * Получить тип запроса для маршрутизации и логирования
     */
    default String getQueryType() {
        return this.getClass().getSimpleName();
    }

    /**
     * Валидация запроса перед выполнением
     * ИСПРАВЛЕНО: Усилена валидация userId для предотвращения атак
     *
     * @throws IllegalArgumentException если запрос невалиден
     */
    default void validate() {
        if (getUserId() == null || getUserId() <= 0 || getUserId() > 999999999999L) {
            throw new IllegalArgumentException("Invalid user ID range");
        }
    }

    /**
     * Получить контекст выполнения запроса (опционально)
     */
    default String getContext() {
        return "DEFAULT";
    }

    /**
     * Определить приоритет запроса для обработки
     */
    default QueryPriority getPriority() {
        return QueryPriority.NORMAL;
    }

    /**
     * Указать, нужно ли кэшировать результат
     */
    default boolean isCacheable() {
        return false;
    }

    /**
     * Получить время жизни кэша в секундах (если применимо)
     */
    default long getCacheTtlSeconds() {
        return 300; // 5 минут по умолчанию
    }

    /**
     * Приоритеты выполнения запросов
     */
    enum QueryPriority {
        HIGH(1),
        NORMAL(5),
        LOW(10);

        private final int level;

        QueryPriority(int level) {
            this.level = level;
        }

        public int getLevel() {
            return level;
        }
    }
}