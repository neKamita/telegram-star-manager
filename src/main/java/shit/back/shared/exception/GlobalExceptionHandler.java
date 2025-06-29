package shit.back.shared.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Глобальный обработчик исключений
 * Заменяет UnifiedExceptionHandler и упрощает обработку ошибок
 * Следует принципам SOLID, DRY, KISS
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Autowired
    private ErrorResponseFactory errorResponseFactory;

    /**
     * Обработка бизнес-исключений
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(
            BusinessException ex,
            HttpServletRequest request) {

        log.warn("Business exception [{}]: {} (correlation: {})",
                ex.getErrorCode(), ex.getMessage(), ex.getCorrelationId());

        HttpStatus status = determineHttpStatus(ex);
        Map<String, Object> errorResponse = errorResponseFactory.createBusinessErrorResponse(ex, request);

        // Логируем критические ошибки
        if (ex.isCritical()) {
            log.error("CRITICAL business exception: {}", ex.toString(), ex);
        }

        return new ResponseEntity<>(errorResponse, status);
    }

    /**
     * Обработка технических исключений
     */
    @ExceptionHandler(TechnicalException.class)
    public ResponseEntity<Map<String, Object>> handleTechnicalException(
            TechnicalException ex,
            HttpServletRequest request) {

        log.error("Technical exception [{}]: {} (correlation: {})",
                ex.getErrorType(), ex.getSystemError(), ex.getCorrelationId(), ex);

        HttpStatus status = ex.isCritical() ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.BAD_GATEWAY;
        Map<String, Object> errorResponse = errorResponseFactory.createTechnicalErrorResponse(ex, request);

        return new ResponseEntity<>(errorResponse, status);
    }

    /**
     * Обработка исключений валидации Spring
     */
    @ExceptionHandler({ MethodArgumentNotValidException.class, BindException.class })
    public ResponseEntity<Map<String, Object>> handleValidationException(
            Exception ex,
            HttpServletRequest request) {

        log.debug("Validation exception: {}", ex.getMessage());

        BusinessException validationEx = BusinessException.validation(
                "Validation failed: " + ex.getMessage(),
                "Проверьте правильность введенных данных");

        Map<String, Object> errorResponse = errorResponseFactory.createBusinessErrorResponse(validationEx, request);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Обработка общих исключений
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unhandled exception: {}", ex.getMessage(), ex);

        TechnicalException technicalEx = TechnicalException.system(
                "Unexpected system error: " + ex.getMessage(), ex);

        Map<String, Object> errorResponse = errorResponseFactory.createTechnicalErrorResponse(technicalEx, request);
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Обработка исключений безопасности
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleSecurityException(
            SecurityException ex,
            HttpServletRequest request) {

        log.warn("Security exception: {} from IP: {}",
                ex.getMessage(), getClientIpAddress(request));

        BusinessException securityEx = BusinessException.security(
                "Security violation: " + ex.getMessage(),
                "Доступ запрещен");

        Map<String, Object> errorResponse = errorResponseFactory.createBusinessErrorResponse(securityEx, request);
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    /**
     * Обработка исключений доступа
     */
    @ExceptionHandler(IllegalAccessException.class)
    public ResponseEntity<Map<String, Object>> handleAccessException(
            IllegalAccessException ex,
            HttpServletRequest request) {

        log.warn("Access exception: {} from IP: {}",
                ex.getMessage(), getClientIpAddress(request));

        BusinessException accessEx = BusinessException.security(
                "Access denied: " + ex.getMessage(),
                "У вас нет прав для выполнения этого действия");

        Map<String, Object> errorResponse = errorResponseFactory.createBusinessErrorResponse(accessEx, request);
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    /**
     * Создание стандартного ответа об ошибке
     */
    public Map<String, Object> createErrorResponse(String message, Exception e) {
        return Map.of(
                "success", false,
                "error", message,
                "message", e != null ? e.getMessage() : "Unknown error",
                "timestamp", LocalDateTime.now(),
                "type", "GENERIC_ERROR");
    }

    /**
     * Определение HTTP статуса на основе бизнес-исключения
     */
    private HttpStatus determineHttpStatus(BusinessException ex) {
        return switch (ex.getSeverity()) {
            case CRITICAL -> HttpStatus.INTERNAL_SERVER_ERROR;
            case HIGH -> HttpStatus.FORBIDDEN;
            case MEDIUM -> HttpStatus.BAD_REQUEST;
            case LOW -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
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

    /**
     * Проверка, является ли запрос AJAX
     */
    private boolean isAjaxRequest(HttpServletRequest request) {
        String requestedWith = request.getHeader("X-Requested-With");
        return "XMLHttpRequest".equals(requestedWith);
    }

    /**
     * Проверка, является ли запрос API
     */
    private boolean isApiRequest(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return requestUri.contains("/api/") ||
                request.getHeader("Accept") != null &&
                        request.getHeader("Accept").contains("application/json");
    }
}