package shit.back.domain.balance.exceptions;

/**
 * Базовое доменное исключение для контекста Balance
 * 
 * Предоставляет общую функциональность для всех доменных исключений
 * в области управления балансом пользователей.
 */
public abstract class BalanceDomainException extends RuntimeException {

    /**
     * Конструктор с сообщением
     */
    protected BalanceDomainException(String message) {
        super(message);
    }

    /**
     * Конструктор с сообщением и причиной
     */
    protected BalanceDomainException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Конструктор с причиной
     */
    protected BalanceDomainException(Throwable cause) {
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