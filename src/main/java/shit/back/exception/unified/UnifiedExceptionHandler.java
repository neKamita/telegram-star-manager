package shit.back.exception.unified;

import shit.back.exception.factory.ExceptionResponseFactory;
import shit.back.exception.mapping.ExceptionMappingStrategy;
import shit.back.exception.registry.ErrorCodeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Унифицированный обработчик исключений
 * Заменяет все существующие обработчики и обеспечивает консистентную обработку
 * ошибок
 * 
 * Использует стратегии маппинга для различных типов исключений
 * и фабрику ответов для создания унифицированных ErrorResponse
 * 
 * @author TelegramStarManager - Stage 3 Refactoring
 * @since Exception Unification Architecture
 */
@ControllerAdvice
@Order(1) // Высший приоритет - обрабатывает все исключения до других handlers
public class UnifiedExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(UnifiedExceptionHandler.class);

    private final ExceptionResponseFactory responseFactory;
    private final List<ExceptionMappingStrategy> mappingStrategies;
    private final ExceptionMetrics metrics;

    @Autowired
    public UnifiedExceptionHandler(
            ExceptionResponseFactory responseFactory,
            List<ExceptionMappingStrategy> mappingStrategies,
            ExceptionMetrics metrics) {
        this.responseFactory = responseFactory;

        // Сортируем стратегии по приоритету (меньше число = выше приоритет)
        this.mappingStrategies = mappingStrategies.stream()
                .sorted((s1, s2) -> Integer.compare(s1.getPriority(), s2.getPriority()))
                .toList();

        this.metrics = metrics;

        log.info("🔧 Унифицированный обработчик исключений инициализирован с {} стратегиями",
                mappingStrategies.size());
    }

    /**
     * Обработка ошибок валидации Spring Boot
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, WebRequest request) {

        log.warn("🔍 Ошибка валидации: {} ошибок в запросе {}",
                ex.getBindingResult().getErrorCount(),
                extractRequestPath(request));

        // Собираем все ошибки валидации
        Map<String, String> validationErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            validationErrors.put(fieldName, errorMessage);
        });

        // Создаем response через фабрику
        ErrorResponse response = responseFactory.createValidationErrorResponse(validationErrors, request);

        // Регистрируем метрики
        metrics.recordException("VALIDATION_ERROR", request);

        // Детальное логирование для отладки
        validationErrors.forEach((field, message) -> log.debug("   ❌ Поле '{}': {}", field, message));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Обработка IllegalArgumentException
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {

        log.warn("⚠️ Некорректный аргумент: {} | Path: {}",
                ex.getMessage(), extractRequestPath(request));

        ErrorResponse response = responseFactory.createFromErrorCode("VAL_003",
                ex.getMessage(), request);

        metrics.recordException("ILLEGAL_ARGUMENT", request);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Главный обработчик - делегирует обработку стратегиям
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, WebRequest request) {

        String requestPath = extractRequestPath(request);

        try {
            // Ищем подходящую стратегию
            Optional<ExceptionMappingStrategy> strategy = findStrategy(ex);

            if (strategy.isPresent()) {
                log.debug("🎯 Найдена стратегия {} для исключения {}",
                        strategy.get().getClass().getSimpleName(),
                        ex.getClass().getSimpleName());

                ErrorResponse response = strategy.get().mapException(ex, request);
                HttpStatus status = ErrorCodeRegistry.getHttpStatus(
                        strategy.get().determineErrorCode(ex));

                // Логируем в зависимости от критичности
                logException(ex, strategy.get().createContext(ex, request), requestPath);

                metrics.recordException(strategy.get().determineErrorCode(ex), request);

                return ResponseEntity.status(status).body(response);
            } else {
                // Fallback для неизвестных исключений
                return handleUnknownException(ex, request);
            }

        } catch (Exception handlingException) {
            // Критическая ошибка в самом обработчике
            log.error("🚨 КРИТИЧЕСКАЯ ОШИБКА в обработчике исключений! " +
                    "Исходное исключение: {} | Ошибка обработки: {}",
                    ex.getClass().getSimpleName(),
                    handlingException.getMessage(), handlingException);

            return handleCriticalHandlerError(ex, handlingException, request);
        }
    }

    /**
     * Поиск подходящей стратегии для исключения
     */
    private Optional<ExceptionMappingStrategy> findStrategy(Throwable exception) {
        return mappingStrategies.stream()
                .filter(strategy -> strategy.canHandle(exception))
                .findFirst();
    }

    /**
     * Обработка неизвестных исключений
     */
    private ResponseEntity<ErrorResponse> handleUnknownException(Exception ex, WebRequest request) {
        String requestPath = extractRequestPath(request);

        log.error("❓ Неизвестное исключение: {} | Message: {} | Path: {}",
                ex.getClass().getSimpleName(), ex.getMessage(), requestPath, ex);

        ErrorResponse response = responseFactory.createGenericErrorResponse(ex, request);
        metrics.recordException("UNKNOWN_ERROR", request);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * Обработка критических ошибок в самом обработчике
     */
    private ResponseEntity<ErrorResponse> handleCriticalHandlerError(
            Exception originalException, Exception handlerException, WebRequest request) {

        // Создаем минимальный response без использования других компонентов
        ErrorResponse.ErrorDetails errorDetails = new ErrorResponse.ErrorDetails();
        errorDetails.setCode("SYS_001");
        errorDetails.setMessage("Критическая системная ошибка");
        errorDetails.setTimestamp(java.time.LocalDateTime.now());
        errorDetails.setTraceId("CRIT-" + java.util.UUID.randomUUID().toString().substring(0, 8));
        errorDetails.setSeverity("CRITICAL");
        errorDetails.setSupportInfo("Немедленно обратитесь в техническую поддержку с ID: " +
                errorDetails.getTraceId());

        ErrorResponse response = new ErrorResponse();
        response.setError(errorDetails);
        response.setPath(extractRequestPath(request));
        response.setMethod("UNKNOWN");

        try {
            metrics.recordCriticalError();
        } catch (Exception metricsException) {
            // Игнорируем ошибки метрик в критической ситуации
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * Логирование исключения в зависимости от контекста
     */
    private void logException(Exception ex, ExceptionContext context, String requestPath) {
        String contextInfo = context != null ? context.toShortString() : "NO_CONTEXT";

        switch (context != null ? context.getSeverity() : ExceptionContext.SeverityLevel.MEDIUM) {
            case CRITICAL -> log.error("🚨 КРИТИЧЕСКОЕ ИСКЛЮЧЕНИЕ {} | {} | Path: {} | Message: {}",
                    contextInfo, ex.getClass().getSimpleName(), requestPath, ex.getMessage(), ex);

            case HIGH -> log.error("🔴 Серьезное исключение {} | {} | Path: {} | Message: {}",
                    contextInfo, ex.getClass().getSimpleName(), requestPath, ex.getMessage());

            case MEDIUM -> log.warn("🟡 Исключение {} | {} | Path: {} | Message: {}",
                    contextInfo, ex.getClass().getSimpleName(), requestPath, ex.getMessage());

            case LOW -> log.info("🟢 Легкое исключение {} | {} | Path: {}",
                    contextInfo, ex.getClass().getSimpleName(), requestPath);
        }

        // Дополнительное логирование контекста для debugging
        if (context != null && log.isDebugEnabled()) {
            log.debug("📋 Контекст исключения: {}", context.toLogMap());
        }
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
     * Получение статистики обработчика для мониторинга
     */
    public Map<String, Object> getHandlerStatistics() {
        return Map.of(
                "strategiesCount", mappingStrategies.size(),
                "strategies", mappingStrategies.stream()
                        .map(s -> s.getClass().getSimpleName())
                        .toList(),
                "metricsEnabled", metrics != null,
                "handlerOrder", 1);
    }
}