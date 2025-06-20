package shit.back.exception.handlers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import shit.back.exception.core.SecurityException;
import shit.back.exception.unified.ErrorResponse;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Специализированный обработчик исключений безопасности
 * Обеспечивает централизованную обработку всех security-related ошибок
 * 
 * @author TelegramStarManager
 * @since Week 3-4 Refactoring
 */
@Slf4j
@ControllerAdvice
public class SecurityExceptionHandler {

    /**
     * Обработка исключений безопасности с детальным логированием
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleSecurityException(
            SecurityException e, WebRequest request) {

        // Логирование события безопасности с correlation ID
        logSecurityEvent(e, request);

        // Определение HTTP статуса на основе типа нарушения
        HttpStatus httpStatus = determineHttpStatus(e.getViolationType());

        // Создание стандартизированного ответа
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorId(e.getCorrelationId())
                .timestamp(e.getTimestamp())
                .status(httpStatus.value())
                .error(ErrorResponse.ErrorDetails.fromMessage("Security Violation"))
                .message(e.getUserFriendlyMessage())
                .path(extractRequestPath(request))
                .details(createSecurityDetails(e))
                .build();

        return ResponseEntity.status(httpStatus).body(errorResponse);
    }

    /**
     * Детальное логирование событий безопасности
     */
    private void logSecurityEvent(SecurityException e, WebRequest request) {
        String logLevel = e.isCritical() ? "ERROR" : "WARN";
        String requestPath = extractRequestPath(request);

        if (e.isCritical()) {
            log.error(
                    "🚨 КРИТИЧЕСКОЕ НАРУШЕНИЕ БЕЗОПАСНОСТИ [{}]: {} | User: {} | Resource: {} | Action: {} | Path: {} | Details: {}",
                    e.getCorrelationId(),
                    e.getViolationType().getDescription(),
                    e.getUserId(),
                    e.getResourceId(),
                    e.getAction(),
                    requestPath,
                    e.getMessage());
        } else {
            log.warn("🔒 Нарушение безопасности [{}]: {} | User: {} | Resource: {} | Action: {} | Path: {}",
                    e.getCorrelationId(),
                    e.getViolationType().getDescription(),
                    e.getUserId(),
                    e.getResourceId(),
                    e.getAction(),
                    requestPath);
        }

        // Дополнительное логирование контекста для аудита
        if (!e.getContext().isEmpty()) {
            log.info("📋 Контекст безопасности [{}]: {}", e.getCorrelationId(), e.getContext());
        }
    }

    /**
     * Определение HTTP статуса на основе типа нарушения безопасности
     */
    private HttpStatus determineHttpStatus(SecurityException.SecurityViolationType violationType) {
        return switch (violationType) {
            case UNAUTHORIZED_ACCESS -> HttpStatus.UNAUTHORIZED;
            case INSUFFICIENT_PERMISSIONS, ADMIN_ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case INVALID_TOKEN -> HttpStatus.UNAUTHORIZED;
            case RATE_LIMIT_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
            case SUSPICIOUS_ACTIVITY -> HttpStatus.FORBIDDEN;
            case SECURITY_LIMIT_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
        };
    }

    /**
     * Создание детальной информации об ошибке безопасности
     */
    private Map<String, Object> createSecurityDetails(SecurityException e) {
        return Map.of(
                "errorType", "SECURITY_VIOLATION",
                "violationType", e.getViolationType().name(),
                "violationCode", e.getViolationType().getCode(),
                "severity", e.getSeverity().name(),
                "correlationId", e.getCorrelationId(),
                "requiresImmediateAttention", e.requiresImmediateAttention(),
                "userFriendlyMessage", e.getUserFriendlyMessage(),
                "actionRequired", generateActionRequired(e.getViolationType()),
                "supportInfo", "При повторных проблемах обратитесь в поддержку с ID: " + e.getCorrelationId());
    }

    /**
     * Генерация рекомендаций по действиям для пользователя
     */
    private String generateActionRequired(SecurityException.SecurityViolationType violationType) {
        return switch (violationType) {
            case UNAUTHORIZED_ACCESS -> "Войдите в систему или обновите токен авторизации";
            case INSUFFICIENT_PERMISSIONS -> "Обратитесь к администратору для получения необходимых прав";
            case INVALID_TOKEN -> "Перелогиньтесь в системе";
            case RATE_LIMIT_EXCEEDED -> "Подождите некоторое время перед повторной попыткой";
            case SUSPICIOUS_ACTIVITY -> "Свяжитесь с поддержкой для разблокировки аккаунта";
            case SECURITY_LIMIT_EXCEEDED -> "Попробуйте позже или обратитесь в поддержку";
            case ADMIN_ACCESS_DENIED -> "Данная функция доступна только администраторам";
        };
    }

    /**
     * Извлечение пути запроса с обработкой ошибок
     */
    private String extractRequestPath(WebRequest request) {
        try {
            return request.getDescription(false).replace("uri=", "");
        } catch (Exception e) {
            log.debug("Не удалось извлечь путь запроса: {}", e.getMessage());
            return "unknown";
        }
    }

    /**
     * Генерация уникального ID для трекинга (если нужен дополнительный)
     */
    private String generateSecurityEventId() {
        return "SEC_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}