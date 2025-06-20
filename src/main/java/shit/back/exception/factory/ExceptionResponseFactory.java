package shit.back.exception.factory;

import shit.back.exception.unified.ErrorResponse;
import shit.back.exception.unified.ExceptionContext;
import shit.back.exception.registry.ErrorCodeRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Фабрика для создания унифицированных ответов об ошибках
 * Обеспечивает консистентность формата ответов для всех типов исключений
 * 
 * @author TelegramStarManager - Stage 3 Refactoring
 * @since Exception Unification Architecture
 */
@Component
public class ExceptionResponseFactory {

    /**
     * Создание ErrorResponse на основе кода ошибки
     */
    public ErrorResponse createFromErrorCode(String errorCode, WebRequest request) {
        return createFromErrorCode(errorCode, null, request);
    }

    /**
     * Создание ErrorResponse на основе кода ошибки с дополнительными деталями
     */
    public ErrorResponse createFromErrorCode(String errorCode, String additionalDetails, WebRequest request) {
        ErrorCodeRegistry.ErrorCodeDefinition definition = ErrorCodeRegistry.getErrorDefinition(errorCode)
                .orElse(createUnknownErrorDefinition(errorCode));

        String traceId = generateTraceId();

        ErrorResponse.ErrorDetails errorDetails = new ErrorResponse.ErrorDetails();
        errorDetails.setCode(errorCode);
        errorDetails.setMessage(definition.getUserMessage());
        errorDetails.setDetails(additionalDetails);
        errorDetails.setTimestamp(LocalDateTime.now());
        errorDetails.setTraceId(traceId);
        errorDetails.setSeverity(definition.getSeverity().name());
        errorDetails.setActionRequired(definition.getActionRequired());
        errorDetails.setSupportInfo(generateSupportInfo(traceId, definition.getSeverity()));

        ErrorResponse response = new ErrorResponse();
        response.setError(errorDetails);
        response.setPath(extractRequestPath(request));
        response.setMethod(extractRequestMethod(request));

        return response;
    }

    /**
     * Создание ErrorResponse на основе контекста исключения
     */
    public ErrorResponse createFromContext(ExceptionContext context, String errorCode, String message) {
        ErrorCodeRegistry.ErrorCodeDefinition definition = ErrorCodeRegistry.getErrorDefinition(errorCode)
                .orElse(createUnknownErrorDefinition(errorCode));

        ErrorResponse.ErrorDetails errorDetails = new ErrorResponse.ErrorDetails();
        errorDetails.setCode(errorCode);
        errorDetails.setMessage(message != null ? message : definition.getUserMessage());
        errorDetails.setTimestamp(context.getTimestamp());
        errorDetails.setTraceId(context.getTraceId());
        errorDetails.setSeverity(context.getSeverity().name());
        errorDetails.setActionRequired(definition.getActionRequired());
        errorDetails.setSupportInfo(generateSupportInfo(context.getTraceId(), context.getSeverity()));

        // Добавляем метаданные из контекста
        if (!context.getAdditionalData().isEmpty()) {
            errorDetails.setMetadata(new HashMap<>(context.getAdditionalData()));
        }

        ErrorResponse response = new ErrorResponse();
        response.setError(errorDetails);
        response.setPath(context.getRequestPath());
        response.setMethod(context.getHttpMethod());

        return response;
    }

    /**
     * Создание ErrorResponse для исключений валидации
     */
    public ErrorResponse createValidationErrorResponse(Map<String, String> validationErrors, WebRequest request) {
        String traceId = generateTraceId();
        String errorCode = "VAL_001";

        ErrorResponse.ErrorDetails errorDetails = new ErrorResponse.ErrorDetails();
        errorDetails.setCode(errorCode);
        errorDetails.setMessage("Ошибка валидации входных данных");
        errorDetails.setTimestamp(LocalDateTime.now());
        errorDetails.setTraceId(traceId);
        errorDetails.setSeverity("LOW");
        errorDetails.setActionRequired("Исправьте указанные ошибки и повторите попытку");
        errorDetails.setSupportInfo(generateSupportInfo(traceId, ExceptionContext.SeverityLevel.LOW));

        // Добавляем детали валидации в метаданные
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("validationErrors", validationErrors);
        metadata.put("invalidFieldsCount", validationErrors.size());
        metadata.put("invalidFields", validationErrors.keySet());
        errorDetails.setMetadata(metadata);

        ErrorResponse response = new ErrorResponse();
        response.setError(errorDetails);
        response.setPath(extractRequestPath(request));
        response.setMethod(extractRequestMethod(request));

        return response;
    }

    /**
     * Создание ErrorResponse для неожиданных исключений
     */
    public ErrorResponse createGenericErrorResponse(Throwable throwable, WebRequest request) {
        String traceId = generateTraceId();
        String errorCode = "SYS_001";

        ErrorResponse.ErrorDetails errorDetails = new ErrorResponse.ErrorDetails();
        errorDetails.setCode(errorCode);
        errorDetails.setMessage("Произошла внутренняя ошибка сервера");
        errorDetails.setTimestamp(LocalDateTime.now());
        errorDetails.setTraceId(traceId);
        errorDetails.setSeverity("CRITICAL");
        errorDetails.setActionRequired("Обратитесь в службу поддержки");
        errorDetails.setSupportInfo(generateCriticalSupportInfo(traceId));

        // Для внутренних ошибок добавляем минимальную техническую информацию
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("exceptionType", throwable.getClass().getSimpleName());
        metadata.put("hasMessage", throwable.getMessage() != null);
        errorDetails.setMetadata(metadata);

        ErrorResponse response = new ErrorResponse();
        response.setError(errorDetails);
        response.setPath(extractRequestPath(request));
        response.setMethod(extractRequestMethod(request));

        return response;
    }

    /**
     * Создание ErrorResponse для конкретного HTTP статуса
     */
    public ErrorResponse createForHttpStatus(HttpStatus status, String message, WebRequest request) {
        String traceId = generateTraceId();
        String errorCode = determineErrorCodeByStatus(status);

        ErrorResponse.ErrorDetails errorDetails = new ErrorResponse.ErrorDetails();
        errorDetails.setCode(errorCode);
        errorDetails.setMessage(message != null ? message : getDefaultMessageForStatus(status));
        errorDetails.setTimestamp(LocalDateTime.now());
        errorDetails.setTraceId(traceId);
        errorDetails.setSeverity(determineSeverityByStatus(status));
        errorDetails.setActionRequired(getActionRequiredForStatus(status));
        errorDetails.setSupportInfo(generateSupportInfo(traceId,
                ExceptionContext.SeverityLevel.valueOf(determineSeverityByStatus(status))));

        ErrorResponse response = new ErrorResponse();
        response.setError(errorDetails);
        response.setPath(extractRequestPath(request));
        response.setMethod(extractRequestMethod(request));

        return response;
    }

    /**
     * Создание простого ErrorResponse с минимальными данными
     */
    public ErrorResponse createSimple(String code, String message, String traceId) {
        ErrorResponse.ErrorDetails errorDetails = new ErrorResponse.ErrorDetails();
        errorDetails.setCode(code);
        errorDetails.setMessage(message);
        errorDetails.setTraceId(traceId != null ? traceId : generateTraceId());
        errorDetails.setTimestamp(LocalDateTime.now());

        ErrorResponse response = new ErrorResponse();
        response.setError(errorDetails);

        return response;
    }

    // === ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ===

    /**
     * Генерация уникального trace ID
     */
    private String generateTraceId() {
        return "TRC-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    /**
     * Создание определения для неизвестной ошибки
     */
    private ErrorCodeRegistry.ErrorCodeDefinition createUnknownErrorDefinition(String code) {
        return new ErrorCodeRegistry.ErrorCodeDefinition(
                code != null ? code : "SYS_003",
                "Неизвестная ошибка",
                "Unknown error",
                HttpStatus.INTERNAL_SERVER_ERROR,
                ExceptionContext.ExceptionCategory.UNKNOWN,
                ExceptionContext.SeverityLevel.MEDIUM,
                "Обратитесь в службу поддержки");
    }

    /**
     * Генерация информации для поддержки
     */
    private String generateSupportInfo(String traceId, ExceptionContext.SeverityLevel severity) {
        return switch (severity) {
            case CRITICAL ->
                String.format("🚨 КРИТИЧЕСКАЯ ОШИБКА! Немедленно обратитесь в поддержку с ID: %s", traceId);
            case HIGH -> String.format("⚠️ При повторении ошибки обратитесь в поддержку с ID: %s", traceId);
            case MEDIUM -> String.format("ℹ️ Если проблема не решается, обратитесь в поддержку с ID: %s", traceId);
            case LOW -> String.format("📝 Справочный ID для поддержки: %s", traceId);
        };
    }

    /**
     * Генерация критической информации для поддержки
     */
    private String generateCriticalSupportInfo(String traceId) {
        return String.format("🚨 КРИТИЧЕСКАЯ СИСТЕМНАЯ ОШИБКА! " +
                "Немедленно сообщите в техническую поддержку ID: %s", traceId);
    }

    /**
     * Извлечение пути запроса
     */
    private String extractRequestPath(WebRequest request) {
        try {
            return request.getDescription(false).replace("uri=", "");
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Извлечение метода запроса
     */
    private String extractRequestMethod(WebRequest request) {
        try {
            // Попробуем извлечь из заголовков или атрибутов
            String method = request.getHeader("X-HTTP-Method-Override");
            if (method != null) {
                return method;
            }

            // Для HTTP servlet requests можем попробовать через Cast
            return "HTTP"; // Fallback
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    /**
     * Определение кода ошибки по HTTP статусу
     */
    private String determineErrorCodeByStatus(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "VAL_001";
            case UNAUTHORIZED -> "SEC_002";
            case FORBIDDEN -> "SEC_001";
            case NOT_FOUND -> "SYS_002";
            case CONFLICT -> "BAL_005";
            case TOO_MANY_REQUESTS -> "SEC_004";
            case INTERNAL_SERVER_ERROR -> "SYS_001";
            case BAD_GATEWAY -> "EXT_001";
            case SERVICE_UNAVAILABLE -> "SYS_002";
            case GATEWAY_TIMEOUT -> "EXT_002";
            default -> "SYS_003";
        };
    }

    /**
     * Получение сообщения по умолчанию для HTTP статуса
     */
    private String getDefaultMessageForStatus(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "Некорректный запрос";
            case UNAUTHORIZED -> "Требуется авторизация";
            case FORBIDDEN -> "Доступ запрещен";
            case NOT_FOUND -> "Ресурс не найден";
            case CONFLICT -> "Конфликт данных";
            case TOO_MANY_REQUESTS -> "Слишком много запросов";
            case INTERNAL_SERVER_ERROR -> "Внутренняя ошибка сервера";
            case BAD_GATEWAY -> "Ошибка внешнего сервиса";
            case SERVICE_UNAVAILABLE -> "Сервис недоступен";
            case GATEWAY_TIMEOUT -> "Таймаут сервиса";
            default -> "Неизвестная ошибка";
        };
    }

    /**
     * Определение уровня критичности по HTTP статусу
     */
    private String determineSeverityByStatus(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST, NOT_FOUND -> "LOW";
            case UNAUTHORIZED, FORBIDDEN, CONFLICT, TOO_MANY_REQUESTS -> "MEDIUM";
            case BAD_GATEWAY, SERVICE_UNAVAILABLE, GATEWAY_TIMEOUT -> "HIGH";
            case INTERNAL_SERVER_ERROR -> "CRITICAL";
            default -> "MEDIUM";
        };
    }

    /**
     * Получение рекомендуемых действий для HTTP статуса
     */
    private String getActionRequiredForStatus(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "Проверьте корректность запроса";
            case UNAUTHORIZED -> "Войдите в систему";
            case FORBIDDEN -> "Обратитесь к администратору для получения прав доступа";
            case NOT_FOUND -> "Проверьте корректность URL";
            case CONFLICT -> "Повторите операцию";
            case TOO_MANY_REQUESTS -> "Подождите и повторите попытку";
            case INTERNAL_SERVER_ERROR -> "Обратитесь в техническую поддержку";
            case BAD_GATEWAY, SERVICE_UNAVAILABLE, GATEWAY_TIMEOUT -> "Попробуйте позже";
            default -> "Обратитесь в службу поддержки";
        };
    }
}