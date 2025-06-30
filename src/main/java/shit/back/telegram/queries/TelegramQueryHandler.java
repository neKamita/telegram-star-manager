package shit.back.telegram.queries;

import shit.back.telegram.dto.TelegramResponse;

/**
 * Базовый интерфейс для всех обработчиков запросов (CQRS Read Side)
 * 
 * @param <T> тип запроса
 */
public interface TelegramQueryHandler<T extends TelegramQuery> {

    /**
     * Обработать запрос
     * 
     * @param query запрос для обработки
     * @return результат обработки
     * @throws Exception если обработка не удалась
     */
    TelegramResponse handle(T query) throws Exception;

    /**
     * Получить класс запроса, который обрабатывает этот handler
     * 
     * @return класс запроса
     */
    Class<T> getQueryType();

    /**
     * Проверить, может ли этот handler обработать данный запрос
     * 
     * @param query запрос для проверки
     * @return true если может обработать
     */
    default boolean canHandle(TelegramQuery query) {
        return getQueryType().isInstance(query);
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
     * Поддерживает ли обработчик кэширование
     */
    default boolean supportsCaching() {
        return false;
    }

    /**
     * Получить описание обработчика для мониторинга
     */
    default String getDescription() {
        return "Query handler for " + getQueryType().getSimpleName();
    }
}