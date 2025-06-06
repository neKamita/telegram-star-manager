package shit.back.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class UserSession {
    private Long userId;
    private String username;
    private String firstName;
    private String lastName;
    private SessionState state;
    private StarPackage selectedPackage;
    private String orderId;
    private LocalDateTime lastActivity;
    
    public enum SessionState {
        IDLE,
        SELECTING_PACKAGE,
        CONFIRMING_ORDER,
        AWAITING_PAYMENT,
        PAYMENT_PROCESSING,
        COMPLETED
    }
    
    public UserSession(Long userId, String username, String firstName, String lastName) {
        this.userId = userId;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.state = SessionState.IDLE;
        this.lastActivity = LocalDateTime.now();
    }
    
    public void updateActivity() {
        this.lastActivity = LocalDateTime.now();
    }
    
    public String getDisplayName() {
        if (firstName != null && !firstName.isEmpty()) {
            return firstName + (lastName != null && !lastName.isEmpty() ? " " + lastName : "");
        }
        return username != null ? username : "Пользователь";
    }
}
