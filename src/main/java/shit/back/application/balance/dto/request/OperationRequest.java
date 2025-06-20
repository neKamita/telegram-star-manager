// Универсальный DTO для всех операций с балансом
package shit.back.application.balance.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public class OperationRequest {

    public enum OperationType {
        DEPOSIT,
        WITHDRAW,
        RESERVE,
        RELEASE,
        REFUND,
        ADJUSTMENT
    }

    @NotNull(message = "Тип операции обязателен")
    private OperationType operationType;

    @NotNull(message = "ID пользователя не может быть null")
    @Positive(message = "ID пользователя должен быть положительным числом")
    private Long userId;

    @NotNull(message = "Сумма не может быть null")
    @DecimalMin(value = "0.01", message = "Минимальная сумма 0.01")
    @DecimalMax(value = "1000000.00", message = "Максимальная сумма 1,000,000.00")
    @Digits(integer = 10, fraction = 2, message = "Некорректный формат суммы")
    private BigDecimal amount;

    @NotBlank(message = "Валюта не может быть пустой")
    @Pattern(regexp = "^(USD|EUR|RUB|STARS)$", message = "Поддерживаемые валюты: USD, EUR, RUB, STARS")
    private String currency;

    // Опциональные поля для разных операций
    private String description;
    private String idempotencyKey;
    private String reason;
    private String orderId;
    private String originalTransactionId;
    private String paymentMethodId;
    private String adminId;
    private String approvalId;
    private LocalDateTime expirationDate;
    private Integer priority;
    private Map<String, Object> metadata;
    private Boolean acceptTerms;

    // Геттеры и сеттеры

    public OperationType getOperationType() {
        return operationType;
    }

    public void setOperationType(OperationType operationType) {
        this.operationType = operationType;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getOriginalTransactionId() {
        return originalTransactionId;
    }

    public void setOriginalTransactionId(String originalTransactionId) {
        this.originalTransactionId = originalTransactionId;
    }

    public String getPaymentMethodId() {
        return paymentMethodId;
    }

    public void setPaymentMethodId(String paymentMethodId) {
        this.paymentMethodId = paymentMethodId;
    }

    public String getAdminId() {
        return adminId;
    }

    public void setAdminId(String adminId) {
        this.adminId = adminId;
    }

    public String getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(String approvalId) {
        this.approvalId = approvalId;
    }

    public LocalDateTime getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(LocalDateTime expirationDate) {
        this.expirationDate = expirationDate;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public Boolean getAcceptTerms() {
        return acceptTerms;
    }

    public void setAcceptTerms(Boolean acceptTerms) {
        this.acceptTerms = acceptTerms;
    }
}