package shit.back.telegram.commands;

import shit.back.telegram.dto.TelegramResponse;

/**
 * Базовый интерфейс для всех обработчиков команд (CQRS Write Side)
 * 
 * @param <T> тип команды
 */
public interface TelegramCommandHandler<T extends TelegramCommand> {

    /**
     * Обработать команду
     * 
     * @param command команда для обработки
     * @return результат обработки
     * @throws Exception если обработка не удалась
     */
    TelegramResponse handle(T command) throws Exception;

    /**
     * Получить класс команды, которую обрабатывает этот handler
     * 
     * @return класс команды
     */
    Class<T> getCommandType();

    /**
     * Проверить, может ли этот handler обработать данную команду
     * 
     * @param command команда для проверки
     * @return true если может обработать
     */
    default boolean canHandle(TelegramCommand command) {
        return getCommandType().isInstance(command);
    }

    /**
     * Получить приоритет обработчика
     * Используется для определения порядка регистрации
     */
    default int getHandlerPriority() {
        return 100;
    }

    /**
     * Проверить активность обработчика
     * Позволяет временно отключить обработчик
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * Получить описание обработчика для мониторинга
     */
    default String getDescription() {
        return "Command handler for " + getCommandType().getSimpleName();
    }
}