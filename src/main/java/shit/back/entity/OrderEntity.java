package shit.back.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_user_id", columnList = "user_id"),
        @Index(name = "idx_orders_status", columnList = "status"),
        @Index(name = "idx_orders_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
public class OrderEntity {

    @Id
    @Column(name = "order_id", length = 8)
    private String orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "star_package_name", length = 50, nullable = false)
    private String starPackageName;

    @Column(name = "star_count", nullable = false)
    private Integer starCount;

    @Column(name = "original_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal originalPrice;

    @Column(name = "discount_percentage")
    private Integer discountPercentage;

    @Column(name = "final_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal finalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "payment_address", length = 500)
    private String paymentAddress;

    @Column(name = "telegram_payment_charge_id", length = 100)
    private String telegramPaymentChargeId;

    @Column(name = "provider_payment_charge_id", length = 100)
    private String providerPaymentChargeId;

    // === ИНТЕГРАЦИЯ С БАЛАНСОМ ===

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_source", length = 20)
    private PaymentSource paymentSource;

    @Column(name = "balance_transaction_id", length = 36)
    private String balanceTransactionId;

    @Column(name = "balance_used", precision = 10, scale = 2)
    private BigDecimal balanceUsed;

    @Column(name = "external_payment", precision = 10, scale = 2)
    private BigDecimal externalPayment;

    @Lob
    @Column(name = "notes")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_by", length = 50)
    private String createdBy = "TELEGRAM_BOT";

    public enum OrderStatus {
        CREATED,
        AWAITING_PAYMENT,
        PAYMENT_RECEIVED,
        PROCESSING,
        COMPLETED,
        FAILED,
        CANCELLED,
        REFUNDED,
        // === НОВЫЕ СТАТУСЫ ДЛЯ ИНТЕГРАЦИИ С БАЛАНСОМ ===
        BALANCE_INSUFFICIENT, // Недостаточно средств на балансе
        PARTIAL_BALANCE_PAYMENT // Частичная оплата балансом
    }

    /**
     * Источник оплаты заказа
     */
    public enum PaymentSource {
        BALANCE, // Полная оплата балансом
        EXTERNAL, // Внешняя оплата (Telegram Stars, крипто)
        MIXED // Комбинированная оплата (баланс + внешняя)
    }

    public OrderEntity(String orderId, Long userId, String username,
            String starPackageName, Integer starCount,
            BigDecimal originalPrice, Integer discountPercentage,
            BigDecimal finalAmount) {
        this.orderId = orderId;
        this.userId = userId;
        this.username = username;
        this.starPackageName = starPackageName;
        this.starCount = starCount;
        this.originalPrice = originalPrice;
        this.discountPercentage = discountPercentage;
        this.finalAmount = finalAmount;
        this.status = OrderStatus.CREATED;
    }

    public void updateStatus(OrderStatus newStatus) {
        this.status = newStatus;
        if (newStatus == OrderStatus.COMPLETED) {
            this.completedAt = LocalDateTime.now();
        }
    }

    public String getFormattedOrderId() {
        return "#" + orderId;
    }

    public String getStatusEmoji() {
        return switch (status) {
            case CREATED -> "🆕";
            case AWAITING_PAYMENT -> "⏳";
            case PAYMENT_RECEIVED -> "💰";
            case PROCESSING -> "⚙️";
            case COMPLETED -> "✅";
            case FAILED -> "❌";
            case CANCELLED -> "🚫";
            case REFUNDED -> "↩️";
            case BALANCE_INSUFFICIENT -> "💳";
            case PARTIAL_BALANCE_PAYMENT -> "🔄";
        };
    }

    public BigDecimal getDiscountAmount() {
        if (discountPercentage == null || discountPercentage == 0) {
            return BigDecimal.ZERO;
        }
        return originalPrice.multiply(BigDecimal.valueOf(discountPercentage))
                .divide(BigDecimal.valueOf(100));
    }

    public boolean isPaymentPending() {
        return status == OrderStatus.CREATED || status == OrderStatus.AWAITING_PAYMENT;
    }

    public boolean isCompleted() {
        return status == OrderStatus.COMPLETED;
    }

    public boolean isCancellable() {
        return status == OrderStatus.CREATED ||
                status == OrderStatus.AWAITING_PAYMENT ||
                status == OrderStatus.PAYMENT_RECEIVED ||
                status == OrderStatus.BALANCE_INSUFFICIENT ||
                status == OrderStatus.PARTIAL_BALANCE_PAYMENT;
    }

    // === МЕТОДЫ ДЛЯ РАБОТЫ С БАЛАНСОМ ===

    /**
     * Проверяет, использовался ли баланс для оплаты заказа
     */
    public boolean isBalanceUsed() {
        return paymentSource == PaymentSource.BALANCE ||
                paymentSource == PaymentSource.MIXED;
    }

    /**
     * Проверяет, является ли оплата полностью балансовой
     */
    public boolean isFullyBalancePaid() {
        return paymentSource == PaymentSource.BALANCE;
    }

    /**
     * Проверяет, является ли оплата смешанной
     */
    public boolean isMixedPayment() {
        return paymentSource == PaymentSource.MIXED;
    }

    /**
     * Получает сумму оплаченную балансом
     */
    public BigDecimal getBalanceUsedAmount() {
        return balanceUsed != null ? balanceUsed : BigDecimal.ZERO;
    }

    /**
     * Получает сумму внешней оплаты
     */
    public BigDecimal getExternalPaymentAmount() {
        return externalPayment != null ? externalPayment : BigDecimal.ZERO;
    }

    /**
     * Устанавливает информацию о балансовой оплате
     */
    public void setBalancePaymentInfo(String transactionId, BigDecimal balanceAmount) {
        this.balanceTransactionId = transactionId;
        this.balanceUsed = balanceAmount;

        if (balanceAmount.compareTo(finalAmount) == 0) {
            // Полная оплата балансом
            this.paymentSource = PaymentSource.BALANCE;
            this.externalPayment = BigDecimal.ZERO;
        } else if (balanceAmount.compareTo(BigDecimal.ZERO) > 0) {
            // Частичная оплата балансом
            this.paymentSource = PaymentSource.MIXED;
            this.externalPayment = finalAmount.subtract(balanceAmount);
        }
    }

    /**
     * Устанавливает информацию о внешней оплате
     */
    public void setExternalPaymentInfo(BigDecimal externalAmount) {
        if (paymentSource == PaymentSource.MIXED) {
            // Уже есть балансовая часть, дополняем внешней
            this.externalPayment = externalAmount;
        } else {
            // Полная внешняя оплата
            this.paymentSource = PaymentSource.EXTERNAL;
            this.externalPayment = externalAmount;
            this.balanceUsed = BigDecimal.ZERO;
        }
    }

    /**
     * Проверяет, требует ли заказ дополнительной оплаты
     */
    public boolean requiresAdditionalPayment() {
        BigDecimal totalPaid = getBalanceUsedAmount().add(getExternalPaymentAmount());
        return totalPaid.compareTo(finalAmount) < 0;
    }

    /**
     * Получает остаток к доплате
     */
    public BigDecimal getRemainingAmount() {
        BigDecimal totalPaid = getBalanceUsedAmount().add(getExternalPaymentAmount());
        BigDecimal remaining = finalAmount.subtract(totalPaid);
        return remaining.compareTo(BigDecimal.ZERO) > 0 ? remaining : BigDecimal.ZERO;
    }
}
