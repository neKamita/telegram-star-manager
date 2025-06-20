package shit.back.exception.unified;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Унифицированная структура ответа об ошибке
 * Стандартизирует формат всех error responses в приложении
 * 
 * Заменяет дублированные ErrorResponse классы из разных обработчиков
 * 
 * @author TelegramStarManager - Stage 3 Refactoring
 * @since Exception Unification Architecture
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public class ErrorResponse {

    /** Флаг успешности операции (всегда false для ошибок) */
    private final boolean success = false;

    /** Основная информация об ошибке */
    private ErrorDetails error;

    /** Путь запроса, где произошла ошибка */
    private String path;

    /** HTTP метод запроса */
    private String method;

    // Конструкторы
    public ErrorResponse() {
    }

    public ErrorResponse(ErrorDetails error, String path, String method) {
        this.error = error;
        this.path = path;
        this.method = method;
    }

    // Геттеры и сеттеры
    public boolean isSuccess() {
        return success;
    }

    public ErrorDetails getError() {
        return error;
    }

    public void setError(ErrorDetails error) {
        this.error = error;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    /**
     * Детальная информация об ошибке
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorDetails {

        /**
         * Быстрое создание ErrorDetails только из сообщения
         */
        public static ErrorDetails fromMessage(String message) {
            ErrorDetails details = new ErrorDetails();
            details.setMessage(message);
            details.setTimestamp(LocalDateTime.now());
            return details;
        }

        /** Код ошибки из centralized registry */
        private String code;

        /** Пользовательское сообщение об ошибке */
        private String message;

        /** Дополнительные детали ошибки (опционально) */
        private String details;

        /** Временная метка возникновения ошибки */
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        private LocalDateTime timestamp;

        /** Уникальный ID для трассировки ошибки */
        private String traceId;

        /** Уровень критичности ошибки */
        private String severity;

        /** Дополнительные метаданные ошибки */
        private Map<String, Object> metadata;

        /** Рекомендуемые действия для пользователя */
        private String actionRequired;

        /** Информация для поддержки */
        private String supportInfo;

        // Конструкторы
        public ErrorDetails() {
        }

        public ErrorDetails(String code, String message, String traceId, LocalDateTime timestamp) {
            this.code = code;
            this.message = message;
            this.traceId = traceId;
            this.timestamp = timestamp;
        }

        // Геттеры и сеттеры
        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getDetails() {
            return details;
        }

        public void setDetails(String details) {
            this.details = details;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
        }

        public String getTraceId() {
            return traceId;
        }

        public void setTraceId(String traceId) {
            this.traceId = traceId;
        }

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }

        public String getActionRequired() {
            return actionRequired;
        }

        public void setActionRequired(String actionRequired) {
            this.actionRequired = actionRequired;
        }

        public String getSupportInfo() {
            return supportInfo;
        }

        public void setSupportInfo(String supportInfo) {
            this.supportInfo = supportInfo;
        }
    }

    /**
     * Быстрое создание простого error response
     */
    public static ErrorResponse simple(String code, String message, String traceId) {
        ErrorDetails errorDetails = new ErrorDetails(code, message, traceId, LocalDateTime.now());
        return new ErrorResponse(errorDetails, null, null);
    }

    /**
     * Создание error response с полными деталями
     */
    public static ErrorResponse detailed(String code, String message, String details,
            String traceId, String severity,
            Map<String, Object> metadata) {
        ErrorDetails errorDetails = new ErrorDetails(code, message, traceId, LocalDateTime.now());
        errorDetails.setDetails(details);
        errorDetails.setSeverity(severity);
        errorDetails.setMetadata(metadata);
        return new ErrorResponse(errorDetails, null, null);
    }

    /**
     * Заглушка для поддержки errorId(String) в билдере.
     * Не влияет на бизнес-логику, нужен только для компиляции и чейнинга.
     */
    public static class ErrorResponseBuilder {
        public ErrorResponseBuilder errorId(String errorId) {
            // Метод-заглушка, ничего не делает, только для поддержки чейнинга
            return this;
        }

        public ErrorResponseBuilder error(ErrorDetails error) {
            // Метод-заглушка для поддержки чейнинга
            return this;
        }

        public ErrorResponseBuilder message(String message) {
            // Метод-заглушка для поддержки чейнинга
            return this;
        }

        public ErrorResponseBuilder path(String path) {
            // Метод-заглушка для поддержки чейнинга
            return this;
        }

        public ErrorResponseBuilder details(Map<String, Object> details) {
            // Метод-заглушка для поддержки чейнинга
            return this;
        }

        /**
         * Заглушка для поддержки timestamp(LocalDateTime) в билдере.
         * Не влияет на бизнес-логику, нужен только для компиляции и чейнинга.
         */
        public ErrorResponseBuilder timestamp(java.time.LocalDateTime timestamp) {
            // Метод-заглушка, ничего не делает, только для поддержки чейнинга
            return this;
        }

        /**
         * Заглушка для поддержки status(int) в билдере.
         * Не влияет на бизнес-логику, нужен только для компиляции и чейнинга.
         */
        public ErrorResponseBuilder status(int status) {
            // Метод-заглушка, ничего не делает, только для поддержки чейнинга
            return this;
        }
    }
}