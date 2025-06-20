package shit.back.exception.mapping;

import shit.back.exception.unified.ErrorResponse;
import shit.back.exception.unified.ExceptionContext;
import org.springframework.web.context.request.WebRequest;

/**
 * Интерфейс стратегии маппинга исключений
 * Определяет контракт для преобразования исключений в унифицированные ответы
 * 
 * @author TelegramStarManager - Stage 3 Refactoring
 * @since Exception Unification Architecture
 */
public interface ExceptionMappingStrategy {

    /**
     * Проверяет, может ли стратегия обработать данное исключение
     */
    boolean canHandle(Throwable exception);

    /**
     * Маппинг исключения в ErrorResponse
     */
    ErrorResponse mapException(Throwable exception, WebRequest request);

    /**
     * Создание контекста исключения
     */
    ExceptionContext createContext(Throwable exception, WebRequest request);

    /**
     * Получение приоритета стратегии (чем меньше число, тем выше приоритет)
     */
    int getPriority();

    /**
     * Определение кода ошибки для исключения
     */
    String determineErrorCode(Throwable exception);
}