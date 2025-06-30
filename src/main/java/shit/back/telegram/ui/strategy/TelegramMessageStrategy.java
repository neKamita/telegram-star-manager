package shit.back.telegram.ui.strategy;

/**
 * Базовая стратегия для форматирования Telegram сообщений
 * 
 * Перенесена из presentation layer для использования в новой архитектуре
 */
public interface TelegramMessageStrategy {

    /**
     * Получить тип стратегии
     */
    String getStrategyType();

    /**
     * Проверить, может ли стратегия обработать данный тип контента
     */
    boolean canHandle(String contentType);

    /**
     * Получить список поддерживаемых типов контента
     */
    String[] getSupportedContentTypes();

    /**
     * Форматировать контент
     */
    String formatContent(String contentType, Object data);
}