package shit.back.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_sessions", indexes = {
        @Index(name = "idx_user_sessions_user_id", columnList = "user_id"),
        @Index(name = "idx_user_sessions_state", columnList = "state"),
        @Index(name = "idx_user_sessions_last_activity", columnList = "last_activity"),
        @Index(name = "idx_user_sessions_active", columnList = "is_active")
})
@Data
@NoArgsConstructor
public class UserSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "language_code", length = 10)
    private String languageCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 30)
    private SessionState state;

    @Column(name = "selected_package_name", length = 50)
    private String selectedPackageName;

    @Column(name = "current_order_id", length = 8)
    private String currentOrderId;

    @Column(name = "last_activity", nullable = false)
    private LocalDateTime lastActivity;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "total_orders", nullable = false)
    private Integer totalOrders = 0;

    @Column(name = "total_stars_purchased", nullable = false)
    private Long totalStarsPurchased = 0L;

    @Column(name = "last_order_date")
    private LocalDateTime lastOrderDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "session_data", length = 1000)
    private String sessionData; // JSON для дополнительных данных

    @Column(name = "payment_type", length = 50)
    private String paymentType; // Выбранный способ оплаты для пополнения баланса

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "ip_address", length = 45) // IPv6 support
    private String ipAddress;

    public enum SessionState {
        IDLE,
        SELECTING_PACKAGE,
        CONFIRMING_ORDER,
        AWAITING_PAYMENT,
        PAYMENT_PROCESSING,
        COMPLETED,
        CANCELLED,
        EXPIRED,
        // === НОВЫЕ СОСТОЯНИЯ ДЛЯ ИНТЕГРАЦИИ С БАЛАНСОМ ===
        TOPPING_UP_BALANCE, // Пополнение баланса
        SELECTING_PAYMENT_TYPE, // Выбор типа оплаты (баланс/внешняя)
        BALANCE_PAYMENT_PROCESSING, // Обработка платежа балансом
        MIXED_PAYMENT_PROCESSING, // Обработка смешанного платежа
        ENTERING_CUSTOM_AMOUNT // Ввод пользовательской суммы пополнения
    }

    public UserSessionEntity(Long userId, String username, String firstName, String lastName) {
        this.userId = userId;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.state = SessionState.IDLE;
        this.lastActivity = LocalDateTime.now();
        this.isActive = true;
    }

    public void updateActivity() {
        this.lastActivity = LocalDateTime.now();
        this.isActive = true;
    }

    public void updateState(SessionState newState) {
        this.state = newState;
        updateActivity();
    }

    public String getDisplayName() {
        if (firstName != null && !firstName.trim().isEmpty()) {
            String fullName = firstName.trim();
            if (lastName != null && !lastName.trim().isEmpty()) {
                fullName += " " + lastName.trim();
            }
            return fullName;
        }
        return username != null ? username : "Пользователь #" + userId;
    }

    public boolean isSessionExpired(int timeoutMinutes) {
        return lastActivity.isBefore(LocalDateTime.now().minusMinutes(timeoutMinutes));
    }

    public void incrementOrderCount() {
        this.totalOrders++;
        this.lastOrderDate = LocalDateTime.now();
    }

    public void addStarsPurchased(long stars) {
        this.totalStarsPurchased += stars;
    }

    public String getActivityStatus() {
        if (!isActive)
            return "Неактивен";

        LocalDateTime now = LocalDateTime.now();
        if (lastActivity.isAfter(now.minusMinutes(5))) {
            return "Онлайн";
        } else if (lastActivity.isAfter(now.minusMinutes(30))) {
            return "Недавно был";
        } else if (lastActivity.isAfter(now.minusHours(24))) {
            return "Был сегодня";
        } else {
            return "Давно не был";
        }
    }

    public String getStateDescription() {
        return switch (state) {
            case IDLE -> "🏠 В меню";
            case SELECTING_PACKAGE -> "📦 Выбирает пакет";
            case CONFIRMING_ORDER -> "✅ Подтверждает заказ";
            case AWAITING_PAYMENT -> "💳 Ожидает оплату";
            case PAYMENT_PROCESSING -> "⚙️ Обработка платежа";
            case COMPLETED -> "✅ Завершено";
            case CANCELLED -> "❌ Отменено";
            case EXPIRED -> "⏰ Истекло";
            case TOPPING_UP_BALANCE -> "💰 Пополняет баланс";
            case SELECTING_PAYMENT_TYPE -> "💳 Выбирает способ оплаты";
            case BALANCE_PAYMENT_PROCESSING -> "💸 Платеж балансом";
            case MIXED_PAYMENT_PROCESSING -> "🔄 Смешанная оплата";
            case ENTERING_CUSTOM_AMOUNT -> "✏️ Вводит сумму";
        };
    }

    public boolean canStartNewOrder() {
        return state == SessionState.IDLE ||
                state == SessionState.COMPLETED ||
                state == SessionState.CANCELLED ||
                state == SessionState.EXPIRED;
    }

    public void deactivate() {
        this.isActive = false;
        this.state = SessionState.EXPIRED;
    }
}
