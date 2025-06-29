package shit.back.shared.exception;

import org.springframework.stereotype.Component;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Фабрика для создания ответов об ошибках
 * Заменяет ExceptionResponseFactory и упрощает создание error responses
 * Следует принципам SOLID, DRY, KISS
 */
@Component
public class ErrorResponseFactory {

    /**
     * Создание ответа для бизнес-исключения
     */
    public Map<String, Object> createBusinessErrorResponse(BusinessException ex, HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();

        response.put("success", false);
        response.put("type", "BUSINESS_ERROR");
        response.put("errorCode", ex.getErrorCode());
        response.put("message", ex.getUserMessage());
        response.put("technicalMessage", ex.getMessage());
        response.put("correlationId", ex.getCorrelationId());
        response.put("severity", ex.getSeverity().name());
        response.put("timestamp", ex.getTimestamp());

        // Добавляем контекст если есть
        if (!ex.getContext().isEmpty()) {
            response.put("context", ex.getContext());
        }

        // Добавляем информацию о запросе
        addRequestContext(response, request);

        return response;
    }

    /**
     * Создание ответа для технического исключения
     */
    public Map<String, Object> createTechnicalErrorResponse(TechnicalException ex, HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();

        response.put("success", false);
        response.put("type", "TECHNICAL_ERROR");
        response.put("errorType", ex.getErrorType().name());
        response.put("message", "Произошла техническая ошибка. Обратитесь к администратору.");
        response.put("correlationId", ex.getCorrelationId());
        response.put("severity", ex.getSeverityLevel());
        response.put("timestamp", ex.getTimestamp());

        // Техническое сообщение только для внутреннего использования
        response.put("technicalMessage", ex.getSystemError());

        // Добавляем информацию о запросе
        addRequestContext(response, request);

        return response;
    }

    /**
     * Создание простого ответа об ошибке
     */
    public Map<String, Object> createResponse(Exception ex) {
        Map<String, Object> response = new HashMap<>();

        response.put("success", false);
        response.put("type", "GENERIC_ERROR");
        response.put("message", "Произошла ошибка");
        response.put("technicalMessage", ex.getMessage());
        response.put("timestamp", LocalDateTime.now());

        return response;
    }

    /**
     * Добавление контекста
     */
    public Map<String, Object> addContext(Map<String, Object> response, Map<String, Object> context) {
        if (context != null && !context.isEmpty()) {
            response.put("context", context);
        }
        return response;
    }

    /**
     * Форматирование сообщения для пользователя
     */
    public String formatForUser(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "Произошла ошибка. Попробуйте еще раз.";
        }

        // Убираем техническую информацию из пользовательского сообщения
        String userMessage = message.replaceAll("(?i)(exception|error|stack|trace)", "");

        // Ограничиваем длину сообщения
        if (userMessage.length() > 200) {
            userMessage = userMessage.substring(0, 197) + "...";
        }

        return userMessage.trim();
    }

    /**
     * Создание успешного ответа
     */
    public Map<String, Object> createSuccessResponse(String message, Object data) {
        Map<String, Object> response = new HashMap<>();

        response.put("success", true);
        response.put("message", message);
        response.put("timestamp", LocalDateTime.now());

        if (data != null) {
            response.put("data", data);
        }

        return response;
    }

    /**
     * Создание ответа валидации
     */
    public Map<String, Object> createValidationErrorResponse(String field, String message) {
        Map<String, Object> response = new HashMap<>();

        response.put("success", false);
        response.put("type", "VALIDATION_ERROR");
        response.put("message", "Ошибка валидации данных");
        response.put("timestamp", LocalDateTime.now());

        Map<String, String> fieldErrors = new HashMap<>();
        fieldErrors.put(field, message);
        response.put("fieldErrors", fieldErrors);

        return response;
    }

    /**
     * Создание ответа для множественных ошибок валидации
     */
    public Map<String, Object> createValidationErrorResponse(Map<String, String> fieldErrors) {
        Map<String, Object> response = new HashMap<>();

        response.put("success", false);
        response.put("type", "VALIDATION_ERROR");
        response.put("message", "Ошибки валидации данных");
        response.put("timestamp", LocalDateTime.now());
        response.put("fieldErrors", fieldErrors);

        return response;
    }

    /**
     * Добавление контекста запроса к ответу
     */
    private void addRequestContext(Map<String, Object> response, HttpServletRequest request) {
        if (request != null) {
            Map<String, Object> requestContext = new HashMap<>();
            requestContext.put("method", request.getMethod());
            requestContext.put("uri", request.getRequestURI());
            requestContext.put("userAgent", request.getHeader("User-Agent"));

            // Добавляем IP адрес
            String clientIp = getClientIpAddress(request);
            requestContext.put("clientIp", clientIp);

            response.put("request", requestContext);
        }
    }

    /**
     * Получение IP адреса клиента
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}