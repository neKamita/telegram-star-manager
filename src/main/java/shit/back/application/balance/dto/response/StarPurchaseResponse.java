package shit.back.application.balance.dto.response;

import lombok.Builder;
import lombok.Data;
import shit.back.domain.balance.valueobjects.Money;

import java.time.LocalDateTime;

/**
 * DTO для ответа о покупке звезд
 */
@Data
@Builder
public class StarPurchaseResponse {
    private String transactionId;
    private Long userId;
    private Integer starCount;
    private Money amount;
    private String status;
    private String paymentMethod;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String errorMessage;

    /**
     * Проверяет, была ли покупка успешной
     */
    public boolean isSuccessful() {
        return "COMPLETED".equals(status) || "SUCCESS".equals(status);
    }

    /**
     * Проверяет, находится ли покупка в процессе
     */
    public boolean isPending() {
        return "PENDING".equals(status) || "PROCESSING".equals(status);
    }

    /**
     * Проверяет, была ли покупка неудачной
     */
    public boolean isFailed() {
        return "FAILED".equals(status) || "ERROR".equals(status);
    }
}