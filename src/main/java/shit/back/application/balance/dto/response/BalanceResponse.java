package shit.back.application.balance.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO для ответа с информацией о балансе
 * Используется в Application Layer для возврата данных клиенту
 */
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
public class BalanceResponse {

    private Long userId;
    private BigDecimal currentBalance;
    private BigDecimal totalDeposited;
    private BigDecimal totalSpent;
    private BigDecimal reservedAmount;
    private String currency;
    private boolean isActive;
    private LocalDateTime lastUpdated;
    private LocalDateTime createdAt;

    // Дополнительные поля для расширенной информации
    private BigDecimal availableBalance; // currentBalance - reservedAmount
    private Long transactionCount;
    private String balanceStatus; // ACTIVE, SUSPENDED, LOCKED

    // Специальный конструктор для простых случаев использования
    public BalanceResponse(Long userId, BigDecimal currentBalance, String currency) {
        this.userId = userId;
        this.currentBalance = currentBalance;
        this.currency = currency;
        this.availableBalance = currentBalance;
        this.isActive = true;
    }

    // Getters и Setters
    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public void setCurrentBalance(BigDecimal currentBalance) {
        this.currentBalance = currentBalance;
    }

    public BigDecimal getTotalDeposited() {
        return totalDeposited;
    }

    public void setTotalDeposited(BigDecimal totalDeposited) {
        this.totalDeposited = totalDeposited;
    }

    public BigDecimal getTotalSpent() {
        return totalSpent;
    }

    public void setTotalSpent(BigDecimal totalSpent) {
        this.totalSpent = totalSpent;
    }

    public BigDecimal getReservedAmount() {
        return reservedAmount;
    }

    public void setReservedAmount(BigDecimal reservedAmount) {
        this.reservedAmount = reservedAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public void setAvailableBalance(BigDecimal availableBalance) {
        this.availableBalance = availableBalance;
    }

    public Long getTransactionCount() {
        return transactionCount;
    }

    public void setTransactionCount(Long transactionCount) {
        this.transactionCount = transactionCount;
    }

    public String getBalanceStatus() {
        return balanceStatus;
    }

    public void setBalanceStatus(String balanceStatus) {
        this.balanceStatus = balanceStatus;
    }

    @Override
    public String toString() {
        return "BalanceResponse{" +
                "userId=" + userId +
                ", currentBalance=" + currentBalance +
                ", totalDeposited=" + totalDeposited +
                ", totalSpent=" + totalSpent +
                ", reservedAmount=" + reservedAmount +
                ", currency='" + currency + '\'' +
                ", isActive=" + isActive +
                ", lastUpdated=" + lastUpdated +
                ", createdAt=" + createdAt +
                ", availableBalance=" + availableBalance +
                ", transactionCount=" + transactionCount +
                ", balanceStatus='" + balanceStatus + '\'' +
                '}';
    }
}