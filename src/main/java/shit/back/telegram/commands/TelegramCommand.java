package shit.back.telegram.commands;

/**
 * Базовый интерфейс для всех Telegram команд (CQRS Write Side)
 * 
 * Унифицированный интерфейс команд для новой функциональной архитектуры
 */
public interface TelegramCommand {

    /**
     * Получить ID пользователя, выполняющего команду
     */
    Long getUserId();

    /**
     * Получить тип команды для маршрутизации и логирования
     */
    default String getCommandType() {
        return this.getClass().getSimpleName();
    }

    /**
     * Валидация команды перед выполнением
     * 
     * @throws IllegalArgumentException если команда невалидна
     */
    default void validate() {
        if (getUserId() == null || getUserId() <= 0) {
            throw new IllegalArgumentException("User ID обязателен и должен быть положительным");
        }
    }

    /**
     * Получить контекст выполнения команды (опционально)
     */
    default String getContext() {
        return "DEFAULT";
    }

    /**
     * Определить приоритет команды для обработки
     */
    default CommandPriority getPriority() {
        return CommandPriority.NORMAL;
    }

    /**
     * Приоритеты выполнения команд
     */
    enum CommandPriority {
        HIGH(1),
        NORMAL(5),
        LOW(10);

        private final int level;

        CommandPriority(int level) {
            this.level = level;
        }

        public int getLevel() {
            return level;
        }
    }
}