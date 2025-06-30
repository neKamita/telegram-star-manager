package shit.back.domain.starPurchase.exceptions;

/**
 * Базовое доменное исключение для контекста StarPurchase
 * 
 * Предоставляет общую функциональность для всех доменных исключений
 * в области управления покупкой звезд через Fragment API.
 */
public abstract class StarPurchaseDomainException extends RuntimeException {

    /**
     * Конструктор с сообщением
     */
    protected StarPurchaseDomainException(String message) {
        super(message);
    }

    /**
     * Конструктор с сообщением и причиной
     */
    protected StarPurchaseDomainException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Конструктор с причиной
     */
    protected StarPurchaseDomainException(Throwable cause) {
        super(cause);
    }

    /**
     * Получение кода ошибки для системы обработки исключений
     */
    public abstract String getErrorCode();

    /**
     * Получение пользовательского сообщения об ошибке
     */
    public abstract String getUserFriendlyMessage();

    /**
     * Проверка, является ли исключение критическим
     * По умолчанию все доменные исключения считаются некритическими
     */
    public boolean isCritical() {
        return false;
    }

    /**
     * Получение контекста ошибки для логирования и мониторинга
     */
    public String getContext() {
        return this.getClass().getSimpleName();
    }

    /**
     * Получение детального сообщения для разработчиков
     */
    public String getDeveloperMessage() {
        return getMessage();
    }
}