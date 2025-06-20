package shit.back.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
public class Order {
    private String orderId;
    private Long userId;
    private String username;
    private StarPackage starPackage;
    private BigDecimal amount;
    private OrderStatus status;
    private String paymentMethod;
    private String paymentAddress;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getOrderId() {
        return orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public StarPackage getStarPackage() {
        return starPackage;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public enum OrderStatus {
        CREATED,
        AWAITING_PAYMENT,
        PAYMENT_RECEIVED,
        PROCESSING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public Order(Long userId, String username, StarPackage starPackage) {
        this.orderId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.userId = userId;
        this.username = username;
        this.starPackage = starPackage;
        this.amount = starPackage.getDiscountedPrice();
        this.status = OrderStatus.CREATED;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void updateStatus(OrderStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
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
        };
    }
}
