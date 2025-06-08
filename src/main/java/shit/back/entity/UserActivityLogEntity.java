package shit.back.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_activity_logs", indexes = {
    @Index(name = "idx_activity_user_id", columnList = "user_id"),
    @Index(name = "idx_activity_timestamp", columnList = "timestamp"),
    @Index(name = "idx_activity_action_type", columnList = "action_type"),
    @Index(name = "idx_activity_is_key", columnList = "is_key_action"),
    @Index(name = "idx_activity_order_id", columnList = "order_id")
})
@Data
@NoArgsConstructor
public class UserActivityLogEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Column(name = "username", length = 100)
    private String username;
    
    @Column(name = "first_name", length = 100)
    private String firstName;
    
    @Column(name = "last_name", length = 100)
    private String lastName;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 30)
    private ActionType actionType;
    
    @Column(name = "action_description", length = 500)
    private String actionDescription;
    
    @Lob
    @Column(name = "action_details")
    private String actionDetails; // JSON с дополнительными данными
    
    @Column(name = "order_id", length = 8)
    private String orderId;
    
    @Column(name = "order_amount", precision = 10, scale = 2)
    private BigDecimal orderAmount;
    
    @Column(name = "star_count")
    private Integer starCount;
    
    @Column(name = "payment_method", length = 50)
    private String paymentMethod;
    
    @Column(name = "session_state_before", length = 30)
    private String sessionStateBefore;
    
    @Column(name = "session_state_after", length = 30)
    private String sessionStateAfter;
    
    @Column(name = "is_key_action", nullable = false)
    private Boolean isKeyAction = false;
    
    @CreationTimestamp
    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    public enum ActionType {
        // Ключевые действия (показываем по умолчанию)
        SESSION_START("🆕", "Начал сессию", true),
        PACKAGE_SELECTED("📦", "Выбрал пакет", true),
        ORDER_CREATED("🛒", "Создал заказ", true),
        PAYMENT_INITIATED("💳", "Начал оплату", true),
        PAYMENT_COMPLETED("💰", "Завершил оплату", true),
        ORDER_COMPLETED("✅", "Заказ выполнен", true),
        ORDER_CANCELLED("❌", "Заказ отменен", true),
        PAYMENT_FAILED("🚫", "Ошибка оплаты", true),
        
        // Все действия (показываем при включении "Show All")
        BOT_MESSAGE_SENT("💬", "Сообщение от бота", false),
        CALLBACK_RECEIVED("🔘", "Нажал кнопку", false),
        STATE_CHANGED("🔄", "Изменил состояние", false),
        SESSION_EXPIRED("⏰", "Сессия истекла", false),
        USER_INPUT_RECEIVED("⌨️", "Ввел данные", false),
        PACKAGE_VIEWED("👀", "Просмотрел пакет", false),
        HELP_REQUESTED("❓", "Запросил помощь", false);
        
        private final String emoji;
        private final String description;
        private final boolean isKeyAction;
        
        ActionType(String emoji, String description, boolean isKeyAction) {
            this.emoji = emoji;
            this.description = description;
            this.isKeyAction = isKeyAction;
        }
        
        public String getEmoji() { return emoji; }
        public String getDescription() { return description; }
        public boolean isKeyAction() { return isKeyAction; }
        
        public String getDisplayName() {
            return emoji + " " + description;
        }
    }
    
    public UserActivityLogEntity(Long userId, String username, ActionType actionType, String actionDescription) {
        this.userId = userId;
        this.username = username;
        this.actionType = actionType;
        this.actionDescription = actionDescription;
        this.isKeyAction = actionType.isKeyAction();
    }
    
    public UserActivityLogEntity(Long userId, String username, String firstName, String lastName, 
                               ActionType actionType, String actionDescription) {
        this(userId, username, actionType, actionDescription);
        this.firstName = firstName;
        this.lastName = lastName;
    }
    
    public String getDisplayName() {
        if (firstName != null && !firstName.trim().isEmpty()) {
            String fullName = firstName.trim();
            if (lastName != null && !lastName.trim().isEmpty()) {
                fullName += " " + lastName.trim();
            }
            return fullName;
        }
        return username != null ? "@" + username : "Пользователь #" + userId;
    }
    
    public String getFormattedTimestamp() {
        return timestamp.toLocalTime().toString().substring(0, 8); // HH:mm:ss
    }
    
    public String getActionIcon() {
        return actionType.getEmoji();
    }
    
    public String getActionDisplayName() {
        return actionType.getDisplayName();
    }
    
    public String getStateChangeDisplay() {
        if (sessionStateBefore != null && sessionStateAfter != null && 
            !sessionStateBefore.equals(sessionStateAfter)) {
            return sessionStateBefore + " → " + sessionStateAfter;
        }
        return sessionStateAfter != null ? "📍 " + sessionStateAfter : "";
    }
    
    public String getOrderDisplayInfo() {
        if (orderId == null) return "";
        
        StringBuilder info = new StringBuilder();
        info.append("#").append(orderId);
        
        if (starCount != null && starCount > 0) {
            info.append(" • ").append(starCount).append("⭐");
        }
        
        if (orderAmount != null) {
            info.append(" • $").append(orderAmount);
        }
        
        if (paymentMethod != null) {
            info.append(" • ").append(paymentMethod);
        }
        
        return info.toString();
    }
    
    public boolean isRecentActivity() {
        return timestamp.isAfter(LocalDateTime.now().minusMinutes(5));
    }
    
    public String getPriorityClass() {
        return switch (actionType) {
            case PAYMENT_COMPLETED, ORDER_COMPLETED -> "success";
            case PAYMENT_FAILED, ORDER_CANCELLED -> "danger";
            case PAYMENT_INITIATED, ORDER_CREATED -> "warning";
            case SESSION_START, PACKAGE_SELECTED -> "info";
            default -> "secondary";
        };
    }
    
    public boolean shouldShowInLiveFeed() {
        return isKeyAction || isRecentActivity();
    }
    
    // Builder pattern methods
    public UserActivityLogEntity withOrderInfo(String orderId, BigDecimal orderAmount, Integer starCount) {
        this.orderId = orderId;
        this.orderAmount = orderAmount;
        this.starCount = starCount;
        return this;
    }
    
    public UserActivityLogEntity withPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
        return this;
    }
    
    public UserActivityLogEntity withStateChange(String before, String after) {
        this.sessionStateBefore = before;
        this.sessionStateAfter = after;
        return this;
    }
    
    public UserActivityLogEntity withDetails(String details) {
        this.actionDetails = details;
        return this;
    }
    
    public UserActivityLogEntity withNetworkInfo(String ipAddress, String userAgent) {
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        return this;
    }
}
