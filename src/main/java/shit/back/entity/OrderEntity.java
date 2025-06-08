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
        REFUNDED
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
               status == OrderStatus.PAYMENT_RECEIVED;
    }
}
