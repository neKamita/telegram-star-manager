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

    public SystemHealth() {
    }

    public SystemHealth(SystemStatus status, Map<String, String> details, LocalDateTime lastChecked,
            List<String> messages) {
        this.status = status;
        this.details = details;
        this.lastChecked = lastChecked;
        this.messages = messages;
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

    @Override
    public String toString() {
        return "SystemHealth{" +
                "status=" + status +
                ", details=" + details +
                ", lastChecked=" + lastChecked +
                ", messages=" + messages +
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
                && Objects.equals(messages, that.messages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, details, lastChecked, messages);
    }
}
