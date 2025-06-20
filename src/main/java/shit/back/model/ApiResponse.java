package shit.back.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Стандартизированный wrapper для всех API ответов
 * Обеспечивает единообразный формат ответов во всем приложении
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;
    private LocalDateTime timestamp;
    private String requestId;
    private Map<String, Object> metadata;

    // Конструкторы
    public ApiResponse() {
        this.timestamp = LocalDateTime.now();
    }

    private ApiResponse(boolean success, String message, T data, String requestId, Map<String, Object> metadata) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now();
        this.requestId = requestId;
        this.metadata = metadata;
    }

    // Статические методы для создания успешных ответов
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "Operation completed successfully", data, null, null);
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, message, data, null, null);
    }

    public static <T> ApiResponse<T> success(T data, String message, Map<String, Object> metadata) {
        return new ApiResponse<>(true, message, data, null, metadata);
    }

    public static <T> ApiResponse<T> success(T data, String message, String requestId) {
        return new ApiResponse<>(true, message, data, requestId, null);
    }

    // Статические методы для создания ответов об ошибках
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, null, null);
    }

    public static <T> ApiResponse<T> error(String message, Map<String, Object> metadata) {
        return new ApiResponse<>(false, message, null, null, metadata);
    }

    public static <T> ApiResponse<T> error(String message, String requestId) {
        return new ApiResponse<>(false, message, null, requestId, null);
    }

    public static <T> ApiResponse<T> error(String message, String requestId, Map<String, Object> metadata) {
        return new ApiResponse<>(false, message, null, requestId, metadata);
    }

    // Методы для создания ResponseEntity
    public ResponseEntity<ApiResponse<T>> toResponseEntity() {
        return ResponseEntity.ok(this);
    }

    public ResponseEntity<ApiResponse<T>> toResponseEntity(HttpStatus status) {
        return ResponseEntity.status(status).body(this);
    }

    public static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(success(data));
    }

    public static <T> ResponseEntity<ApiResponse<T>> ok(T data, String message) {
        return ResponseEntity.ok(success(data, message));
    }

    public static <T> ResponseEntity<ApiResponse<T>> created(T data) {
        return ResponseEntity.status(HttpStatus.CREATED).body(success(data, "Resource created successfully"));
    }

    public static <T> ResponseEntity<ApiResponse<T>> created(T data, String message) {
        return ResponseEntity.status(HttpStatus.CREATED).body(success(data, message));
    }

    public static <T> ResponseEntity<ApiResponse<T>> badRequest(String message) {
        return ResponseEntity.badRequest().body(error(message));
    }

    public static <T> ResponseEntity<ApiResponse<T>> badRequest(String message, Map<String, Object> metadata) {
        return ResponseEntity.badRequest().body(error(message, metadata));
    }

    public static <T> ResponseEntity<ApiResponse<T>> notFound(String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error(message));
    }

    public static <T> ResponseEntity<ApiResponse<T>> forbidden(String message) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(message));
    }

    public static <T> ResponseEntity<ApiResponse<T>> unauthorized(String message) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error(message));
    }

    public static <T> ResponseEntity<ApiResponse<T>> conflict(String message) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error(message));
    }

    public static <T> ResponseEntity<ApiResponse<T>> paymentRequired(String message) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(error(message, Map.of(
                "actionRequired", "Пополните баланс для продолжения",
                "errorType", "INSUFFICIENT_BALANCE")));
    }

    public static <T> ResponseEntity<ApiResponse<T>> tooManyRequests(String message) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error(message, Map.of(
                "actionRequired", "Попробуйте позже",
                "errorType", "RATE_LIMIT_EXCEEDED")));
    }

    public static <T> ResponseEntity<ApiResponse<T>> internalServerError(String message) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error(message));
    }

    public static <T> ResponseEntity<ApiResponse<T>> internalServerError(String message, String requestId) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error(message, requestId, Map.of(
                "supportInfo", "Сообщите ID запроса в службу поддержки: " + requestId,
                "errorType", "INTERNAL_SERVER_ERROR")));
    }

    // Методы для добавления метаданных
    public ApiResponse<T> withMetadata(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new java.util.HashMap<>();
        }
        this.metadata.put(key, value);
        return this;
    }

    public ApiResponse<T> withMetadata(Map<String, Object> metadata) {
        if (metadata != null) {
            if (this.metadata == null) {
                this.metadata = new java.util.HashMap<>();
            }
            this.metadata.putAll(metadata);
        }
        return this;
    }

    public ApiResponse<T> withRequestId(String requestId) {
        this.requestId = requestId;
        return this;
    }

    // Методы для работы с пагинацией
    public static <T> ApiResponse<T> page(T data, long totalElements, int page, int size) {
        return success(data, "Page retrieved successfully", Map.of(
                "pagination", Map.of(
                        "totalElements", totalElements,
                        "currentPage", page,
                        "pageSize", size,
                        "totalPages", (totalElements + size - 1) / size)));
    }

    // Геттеры и сеттеры
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    @Override
    public String toString() {
        return String.format(
                "ApiResponse{success=%s, message='%s', data=%s, timestamp=%s, requestId='%s'}",
                success, message, data, timestamp, requestId);
    }
}