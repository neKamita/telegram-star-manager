package shit.back.exception.mapping;

import shit.back.exception.core.BaseBusinessException;
import shit.back.exception.unified.ErrorResponse;
import shit.back.exception.unified.ExceptionContext;
import shit.back.exception.factory.ExceptionResponseFactory;
import shit.back.exception.registry.ErrorCodeRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;

/**
 * Стратегия маппинга бизнес-исключений
 * Обрабатывает все исключения наследующие BaseBusinessException
 * 
 * @author TelegramStarManager - Stage 3 Refactoring
 * @since Exception Unification Architecture
 */
@Component
public class BusinessExceptionMappingStrategy implements ExceptionMappingStrategy {

    private final ExceptionResponseFactory responseFactory;

    @Autowired
    public BusinessExceptionMappingStrategy(ExceptionResponseFactory responseFactory) {
        this.responseFactory = responseFactory;
    }

    @Override
    public boolean canHandle(Throwable exception) {
        return exception instanceof BaseBusinessException;
    }

    @Override
    public ErrorResponse mapException(Throwable exception, WebRequest request) {
        BaseBusinessException businessException = (BaseBusinessException) exception;

        // Создаем контекст исключения
        ExceptionContext context = createContext(exception, request);

        // Используем фабрику для создания response
        ErrorResponse response = responseFactory.createFromContext(
                context,
                businessException.getErrorCode(),
                businessException.getUserFriendlyMessage());

        // Добавляем специфичные для бизнес-исключений метаданные
        enhanceWithBusinessMetadata(response, businessException);

        return response;
    }

    @Override
    public ExceptionContext createContext(Throwable exception, WebRequest request) {
        BaseBusinessException businessException = (BaseBusinessException) exception;

        ExceptionContext context = new ExceptionContext(
                ExceptionContext.ExceptionCategory.BUSINESS_LOGIC,
                convertSeverity(businessException.getSeverity()));

        // Установка HTTP контекста
        context.withHttpContext(
                extractHttpMethod(request),
                extractRequestPath(request),
                extractClientIp(request));

        // Установка пользовательского контекста (если доступен)
        String userId = extractUserId(request);
        String sessionId = extractSessionId(request);
        context.withUserContext(userId, sessionId);

        // Добавление данных из business exception
        context.addAllData(businessException.getContext());
        context.addData("errorCode", businessException.getErrorCode());
        context.addData("correlationId", businessException.getCorrelationId());
        context.addData("originalTimestamp", businessException.getTimestamp());

        // Добавление стек-трейса для высоких уровней критичности
        if (businessException.isCritical()) {
            context.withStackTrace(exception);
        }

        return context;
    }

    @Override
    public int getPriority() {
        return 100; // Высокий приоритет для специфичных бизнес-исключений
    }

    @Override
    public String determineErrorCode(Throwable exception) {
        if (exception instanceof BaseBusinessException businessException) {
            return businessException.getErrorCode();
        }
        return "SYS_001"; // Fallback
    }

    /**
     * Улучшение response метаданными бизнес-исключения
     */
    private void enhanceWithBusinessMetadata(ErrorResponse response, BaseBusinessException businessException) {
        if (response.getError().getMetadata() == null) {
            response.getError().setMetadata(new java.util.HashMap<>());
        }

        response.getError().getMetadata().putAll(java.util.Map.of(
                "businessExceptionType", businessException.getClass().getSimpleName(),
                "correlationId", businessException.getCorrelationId(),
                "severity", businessException.getSeverity().name(),
                "isCritical", businessException.isCritical(),
                "requiresImmediateAttention", businessException.requiresImmediateAttention(),
                "originalTimestamp", businessException.getTimestamp().toString()));

        // Добавляем контекст если он не пустой
        if (!businessException.getContext().isEmpty()) {
            response.getError().getMetadata().put("businessContext", businessException.getContext());
        }

        // Устанавливаем дополнительные детали для критичных ошибок
        if (businessException.isCritical()) {
            response.getError().setDetails("Критическая бизнес-ошибка требует немедленного внимания");
        }
    }

    /**
     * Конверсия уровня критичности из BaseBusinessException в ExceptionContext
     */
    private ExceptionContext.SeverityLevel convertSeverity(BaseBusinessException.ErrorSeverity severity) {
        return switch (severity) {
            case LOW -> ExceptionContext.SeverityLevel.LOW;
            case MEDIUM -> ExceptionContext.SeverityLevel.MEDIUM;
            case HIGH -> ExceptionContext.SeverityLevel.HIGH;
            case CRITICAL -> ExceptionContext.SeverityLevel.CRITICAL;
        };
    }

    // === ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ИЗВЛЕЧЕНИЯ КОНТЕКСТА ===

    private String extractHttpMethod(WebRequest request) {
        try {
            String method = request.getHeader("X-HTTP-Method-Override");
            return method != null ? method : "HTTP";
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private String extractRequestPath(WebRequest request) {
        try {
            return request.getDescription(false).replace("uri=", "");
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String extractClientIp(WebRequest request) {
        try {
            // Попробуем извлечь IP из заголовков
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0].trim();
            }

            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isEmpty()) {
                return xRealIp;
            }

            // Попробуем другие заголовки
            String cfConnectingIp = request.getHeader("CF-Connecting-IP");
            if (cfConnectingIp != null && !cfConnectingIp.isEmpty()) {
                return cfConnectingIp;
            }

            return "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String extractUserId(WebRequest request) {
        try {
            // Попробуем извлечь из заголовков или параметров
            String userId = request.getHeader("X-User-ID");
            if (userId != null) {
                return userId;
            }

            // Можно добавить другие способы извлечения user ID
            // например, из JWT токена или сессии

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractSessionId(WebRequest request) {
        try {
            String sessionId = request.getHeader("X-Session-ID");
            if (sessionId != null) {
                return sessionId;
            }

            // Можно добавить извлечение session ID из cookies или других источников

            return null;
        } catch (Exception e) {
            return null;
        }
    }
}