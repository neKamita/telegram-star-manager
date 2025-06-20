package shit.back.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Сущность платежа в системе Telegram Star Manager
 *
 * Хранит информацию о платежах через различные платежные системы
 */
@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payments_user_id", columnList = "user_id"),
        @Index(name = "idx_payments_payment_id", columnList = "payment_id", unique = true),
        @Index(name = "idx_payments_external_id", columnList = "external_payment_id"),
        @Index(name = "idx_payments_status", columnList = "status"),
        @Index(name = "idx_payments_method", columnList = "payment_method"),
        @Index(name = "idx_payments_created", columnList = "created_at"),
        @Index(name = "idx_payments_updated", columnList = "updated_at")
})
@Data
public class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Уникальный ID платежа в нашей системе
     */
    @Column(name = "payment_id", length = 50, nullable = false, unique = true)
    @NotBlank(message = "ID платежа не может быть пустым")
    @Size(max = 50, message = "ID платежа не может превышать 50 символов")
    private String paymentId;

    /**
     * ID пользователя, который совершает платеж
     */
    @Column(name = "user_id", nullable = false)
    @NotNull(message = "ID пользователя не может быть пустым")
    private Long userId;

    /**
     * ID заказа (может быть связан с конкретным заказом или пополнением баланса)
     */
    @Column(name = "order_id", length = 50)
    @Size(max = 50, message = "ID заказа не может превышать 50 символов")
    private String orderId;

    /**
     * Сумма платежа
     */
    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    @NotNull(message = "Сумма платежа не может быть пустой")
    @DecimalMin(value = "0.01", message = "Сумма платежа должна быть больше 0")
    @Digits(integer = 10, fraction = 2, message = "Неверный формат суммы")
    private BigDecimal amount;

    /**
     * Валюта платежа
     */
    @Column(name = "currency", length = 3, nullable = false)
    @NotBlank(message = "Валюта не может быть пустой")
    @Size(min = 3, max = 3, message = "Код валюты должен состоять из 3 символов")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Код валюты должен содержать только заглавные латинские буквы")
    private String currency = "USD";

    /**
     * Способ оплаты (TON, YooKassa, Qiwi, SberPay, etc.)
     */
    @Column(name = "payment_method", length = 30, nullable = false)
    @NotBlank(message = "Способ оплаты не может быть пустым")
    @Size(max = 30, message = "Способ оплаты не может превышать 30 символов")
    private String paymentMethod;

    /**
     * Статус платежа
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @NotNull(message = "Статус платежа не может быть пустым")
    private PaymentStatus status = PaymentStatus.PENDING;

    /**
     * Внешний ID платежа в платежной системе
     */
    @Column(name = "external_payment_id", length = 100)
    @Size(max = 100, message = "Внешний ID платежа не может превышать 100 символов")
    private String externalPaymentId;

    /**
     * URL для оплаты (если применимо)
     */
    @Column(name = "payment_url", length = 500)
    @Size(max = 500, message = "URL для оплаты не может превышать 500 символов")
    private String paymentUrl;

    /**
     * Время истечения платежа
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * Дополнительные данные платежа в формате JSON
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    @Size(max = 2000, message = "Метаданные не могут превышать 2000 символов")
    private String metadata;

    /**
     * Описание платежа
     */
    @Column(name = "description", length = 200)
    @Size(max = 200, message = "Описание не может превышать 200 символов")
    private String description;

    /**
     * Сообщение об ошибке (если платеж неуспешен)
     */
    @Column(name = "error_message", length = 500)
    @Size(max = 500, message = "Сообщение об ошибке не может превышать 500 символов")
    private String errorMessage;

    /**
     * Количество попыток обработки
     */
    @Column(name = "retry_count", nullable = false)
    @Min(value = 0, message = "Количество попыток не может быть отрицательным")
    private Integer retryCount = 0;

    /**
     * IP адрес пользователя
     */
    @Column(name = "user_ip", length = 45)
    @Size(max = 45, message = "IP адрес не может превышать 45 символов")
    private String userIp;

    /**
     * User-Agent пользователя
     */
    @Column(name = "user_agent", length = 500)
    @Size(max = 500, message = "User-Agent не может превышать 500 символов")
    private String userAgent;

    /**
     * Время создания платежа
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Время последнего обновления
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Время завершения платежа
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * Конструктор по умолчанию
     */
    public PaymentEntity() {
    }

    /**
     * Конструктор для создания нового платежа
     */
    public PaymentEntity(String paymentId, Long userId, BigDecimal amount, String currency, String paymentMethod) {
        this.paymentId = paymentId;
        this.userId = userId;
        this.amount = amount;
        this.currency = currency;
        this.paymentMethod = paymentMethod;
        this.status = PaymentStatus.PENDING;
        this.retryCount = 0;
    }

    /**
     * Конструктор с заказом
     */
    public PaymentEntity(String paymentId, Long userId, String orderId, BigDecimal amount,
            String currency, String paymentMethod, String description) {
        this(paymentId, userId, amount, currency, paymentMethod);
        this.orderId = orderId;
        this.description = description;
    }

    /**
     * Обновить статус платежа
     */
    public void updateStatus(PaymentStatus newStatus) {
        this.status = newStatus;
        if (newStatus.isFinal()) {
            this.completedAt = LocalDateTime.now();
        }
    }

    /**
     * Обновить статус платежа с сообщением об ошибке
     */
    public void updateStatus(PaymentStatus newStatus, String errorMessage) {
        updateStatus(newStatus);
        this.errorMessage = errorMessage;
    }

    /**
     * Установить внешний ID и URL платежа
     */
    public void setExternalData(String externalPaymentId, String paymentUrl) {
        this.externalPaymentId = externalPaymentId;
        this.paymentUrl = paymentUrl;
    }

    /**
     * Увеличить счетчик попыток
     */
    public void incrementRetryCount() {
        this.retryCount++;
    }

    /**
     * Установить время истечения
     */
    public void setExpiresAt(int minutesFromNow) {
        this.expiresAt = LocalDateTime.now().plusMinutes(minutesFromNow);
    }

    /**
     * Проверить, истек ли платеж
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Получить форматированную сумму
     */
    public String getFormattedAmount() {
        return String.format("%.2f %s", amount, currency);
    }

    /**
     * Получить форматированный статус
     */
    public String getFormattedStatus() {
        return status.getFormattedStatus();
    }

    /**
     * Проверить, можно ли отменить платеж
     */
    public boolean isCancellable() {
        return status.isCancellable() && !isExpired();
    }

    /**
     * Проверить, завершен ли платеж успешно
     */
    public boolean isSuccessful() {
        return status.isSuccessful();
    }

    /**
     * Проверить, требует ли платеж обработки
     */
    public boolean requiresProcessing() {
        return !status.isFinal() && !isExpired();
    }

    // Публичные геттеры для совместимости с IDE (дополнительно к Lombok)
    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getPaymentUrl() {
        return paymentUrl;
    }

    public void setPaymentUrl(String paymentUrl) {
        this.paymentUrl = paymentUrl;
    }
}