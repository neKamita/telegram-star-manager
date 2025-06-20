package shit.back.exception.handlers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import shit.back.exception.core.BaseBusinessException;
import shit.back.exception.core.ValidationException;
import shit.back.exception.unified.ErrorResponse;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;

/**
 * Специализированный обработчик бизнес-исключений
 * Обрабатывает все исключения связанные с бизнес-логикой приложения
 * 
 * @author TelegramStarManager
 * @since Week 3-4 Refactoring
 */
@Slf4j
@ControllerAdvice
public class BusinessExceptionHandler {

    /**
     * Обработка базовых бизнес-исключений
     */
    @ExceptionHandler(BaseBusinessException.class)
    public ResponseEntity<ErrorResponse> handleBaseBusinessException(
            BaseBusinessException e, WebRequest request) {

        // Логирование в зависимости от критичности
        logBusinessException(e, request);

        // Определение HTTP статуса
        HttpStatus httpStatus = determineHttpStatusForBusiness(e);

        // Создание стандартизированного ответа
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorId(e.getCorrelationId())
                .timestamp(e.getTimestamp())
                .status(httpStatus.value())
                .error(ErrorResponse.ErrorDetails.fromMessage("Business Logic Error"))
                .message(e.getUserFriendlyMessage())
                .path(extractRequestPath(request))
                .details(createBusinessDetails(e))
                .build();

        return ResponseEntity.status(httpStatus).body(errorResponse);
    }

    /**
     * Специализированная обработка исключений валидации
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            ValidationException e, WebRequest request) {

        log.warn("🔍 Ошибка валидации [{}]: {} ошибок для объекта '{}' | Path: {}",
                e.getCorrelationId(),
                e.getErrorCount(),
                e.getValidatedObject(),
                extractRequestPath(request));

        // Детальное логирование ошибок валидации для отладки
        e.getValidationErrors().forEach(error -> log.debug("   ❌ Поле '{}': '{}' - {}",
                error.getField(),
                error.getInvalidValue(),
                error.getConstraint()));

        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorId(e.getCorrelationId())
                .timestamp(e.getTimestamp())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(ErrorResponse.ErrorDetails.fromMessage("Validation Error"))
                .message(e.getUserFriendlyMessage())
                .path(extractRequestPath(request))
                .details(createValidationDetails(e))
                .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Логирование бизнес-исключений с учетом критичности
     */
    private void logBusinessException(BaseBusinessException e, WebRequest request) {
        String requestPath = extractRequestPath(request);

        if (e.isCritical()) {
            log.error("🚨 КРИТИЧЕСКАЯ БИЗНЕС-ОШИБКА [{}]: {} | Severity: {} | Path: {} | Context: {}",
                    e.getCorrelationId(),
                    e.getMessage(),
                    e.getSeverity().getDisplayName(),
                    requestPath,
                    e.getContext());
        } else if (e.requiresImmediateAttention()) {
            log.warn("⚠️ Серьезная бизнес-ошибка [{}]: {} | Severity: {} | Path: {}",
                    e.getCorrelationId(),
                    e.getMessage(),
                    e.getSeverity().getDisplayName(),
                    requestPath);
        } else {
            log.info("ℹ️ Бизнес-ошибка [{}]: {} | Severity: {} | Path: {}",
                    e.getCorrelationId(),
                    e.getMessage(),
                    e.getSeverity().getDisplayName(),
                    requestPath);
        }
    }

    /**
     * Определение HTTP статуса для бизнес-исключений
     */
    private HttpStatus determineHttpStatusForBusiness(BaseBusinessException e) {
        // Определяем статус на основе критичности и типа ошибки
        if (e.isCritical()) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }

        // Проверяем по коду ошибки
        String errorCode = e.getErrorCode();
        if (errorCode.startsWith("VAL_")) {
            return HttpStatus.BAD_REQUEST;
        } else if (errorCode.startsWith("SEC_")) {
            return HttpStatus.FORBIDDEN;
        } else if (errorCode.startsWith("BAL_")) {
            return HttpStatus.PAYMENT_REQUIRED;
        }

        // По умолчанию - ошибка запроса
        return HttpStatus.BAD_REQUEST;
    }

    /**
     * Создание детальной информации для бизнес-исключений
     */
    private Map<String, Object> createBusinessDetails(BaseBusinessException e) {
        Map<String, Object> details = new HashMap<>();

        details.put("errorType", "BUSINESS_LOGIC_ERROR");
        details.put("errorCode", e.getErrorCode());
        details.put("severity", e.getSeverity().name());
        details.put("severityDisplay", e.getSeverity().getDisplayName());
        details.put("correlationId", e.getCorrelationId());
        details.put("isCritical", e.isCritical());
        details.put("requiresImmediateAttention", e.requiresImmediateAttention());
        details.put("timestamp", e.getTimestamp().toString());
        details.put("userFriendlyMessage", e.getUserFriendlyMessage());

        // Добавляем контекст если есть
        if (!e.getContext().isEmpty()) {
            details.put("context", e.getContext());
        }

        // Рекомендации для пользователя
        details.put("supportInfo", generateSupportInfo(e));

        return details;
    }

    /**
     * Создание детальной информации для ошибок валидации
     */
    private Map<String, Object> createValidationDetails(ValidationException e) {
        Map<String, Object> details = new HashMap<>();

        details.put("errorType", "VALIDATION_ERROR");
        details.put("validationType", e.getValidationType().name());
        details.put("validationTypeDisplay", e.getValidationType().getDescription());
        details.put("validatedObject", e.getValidatedObject());
        details.put("errorCount", e.getErrorCount());
        details.put("correlationId", e.getCorrelationId());

        // Детализация ошибок валидации
        Map<String, Object> validationErrors = new HashMap<>();
        e.getValidationErrors().forEach(error -> {
            validationErrors.put(error.getField(), Map.of(
                    "invalidValue", error.getInvalidValue(),
                    "constraint", error.getConstraint()));
        });
        details.put("validationErrors", validationErrors);

        // Список полей с ошибками для удобства фронтенда
        details.put("invalidFields", e.getValidationErrors().stream()
                .map(ValidationException.ValidationError::getField)
                .distinct()
                .toList());

        details.put("userFriendlyMessage", e.getUserFriendlyMessage());
        details.put("actionRequired", "Исправьте указанные ошибки и повторите попытку");

        return details;
    }

    /**
     * Генерация информации для поддержки
     */
    private String generateSupportInfo(BaseBusinessException e) {
        if (e.isCritical()) {
            return String.format("Критическая ошибка! Немедленно свяжитесь с поддержкой. ID: %s",
                    e.getCorrelationId());
        } else if (e.requiresImmediateAttention()) {
            return String.format("При повторении ошибки обратитесь в поддержку. ID: %s",
                    e.getCorrelationId());
        } else {
            return String.format("ID для поддержки: %s", e.getCorrelationId());
        }
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
}