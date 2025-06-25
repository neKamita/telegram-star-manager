package shit.back.dto.monitoring;

import java.io.Serializable;
import shit.back.dto.monitoring.SystemStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SystemHealth implements Serializable {
    private static final long serialVersionUID = 1L;

    private SystemStatus status;
    private Map<String, String> details;
    private LocalDateTime lastChecked;
    private List<String> messages;

    // Добавляем недостающие поля для пользовательской статистики
    private Long totalUsers;
    private Long activeUsers;
    private Long onlineUsers;
    private Long totalOrders;
    private Integer healthScore;

    public SystemHealth() {
    }

    public SystemHealth(SystemStatus status, Map<String, String> details, LocalDateTime lastChecked,
            List<String> messages) {
        this.status = status;
        this.details = details;
        this.lastChecked = lastChecked;
        this.messages = messages;
    }

    public SystemHealth(SystemStatus status, Map<String, String> details, LocalDateTime lastChecked,
            List<String> messages, Long totalUsers, Long activeUsers, Long onlineUsers, Long totalOrders,
            Integer healthScore) {
        this.status = status;
        this.details = details;
        this.lastChecked = lastChecked;
        this.messages = messages;
        this.totalUsers = totalUsers;
        this.activeUsers = activeUsers;
        this.onlineUsers = onlineUsers;
        this.totalOrders = totalOrders;
        this.healthScore = healthScore;
    }

    public SystemStatus getStatus() {
        return status;
    }

    public void setStatus(SystemStatus status) {
        this.status = status;
    }

    public Map<String, String> getDetails() {
        return details;
    }

    public void setDetails(Map<String, String> details) {
        this.details = details;
    }

    public LocalDateTime getLastChecked() {
        return lastChecked;
    }

    public void setLastChecked(LocalDateTime lastChecked) {
        this.lastChecked = lastChecked;
    }

    public List<String> getMessages() {
        return messages;
    }

    public void setMessages(List<String> messages) {
        this.messages = messages;
    }

    public Long getTotalUsers() {
        return totalUsers;
    }

    public void setTotalUsers(Long totalUsers) {
        this.totalUsers = totalUsers;
    }

    public Long getActiveUsers() {
        return activeUsers;
    }

    public void setActiveUsers(Long activeUsers) {
        this.activeUsers = activeUsers;
    }

    public Long getOnlineUsers() {
        return onlineUsers;
    }

    public void setOnlineUsers(Long onlineUsers) {
        this.onlineUsers = onlineUsers;
    }

    public Long getTotalOrders() {
        return totalOrders;
    }

    public void setTotalOrders(Long totalOrders) {
        this.totalOrders = totalOrders;
    }

    public Integer getHealthScore() {
        return healthScore;
    }

    public void setHealthScore(Integer healthScore) {
        this.healthScore = healthScore;
    }

    @Override
    public String toString() {
        return "SystemHealth{" +
                "status=" + status +
                ", details=" + details +
                ", lastChecked=" + lastChecked +
                ", messages=" + messages +
                ", totalUsers=" + totalUsers +
                ", activeUsers=" + activeUsers +
                ", onlineUsers=" + onlineUsers +
                ", totalOrders=" + totalOrders +
                ", healthScore=" + healthScore +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof SystemHealth))
            return false;
        SystemHealth that = (SystemHealth) o;
        return status == that.status
                && Objects.equals(details, that.details)
                && Objects.equals(lastChecked, that.lastChecked)
                && Objects.equals(messages, that.messages)
                && Objects.equals(totalUsers, that.totalUsers)
                && Objects.equals(activeUsers, that.activeUsers)
                && Objects.equals(onlineUsers, that.onlineUsers)
                && Objects.equals(totalOrders, that.totalOrders)
                && Objects.equals(healthScore, that.healthScore);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, details, lastChecked, messages, totalUsers, activeUsers, onlineUsers, totalOrders,
                healthScore);
    }
}
