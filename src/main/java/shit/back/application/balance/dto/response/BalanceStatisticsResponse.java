package shit.back.application.balance.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO для ответа со статистикой баланса
 * Используется в Application Layer для возврата аналитических данных
 */
public class BalanceStatisticsResponse {

    // Основная статистика баланса
    private BigDecimal currentBalance;
    private BigDecimal totalDeposited;
    private BigDecimal totalSpent;
    private BigDecimal reservedAmount;
    private BigDecimal availableBalance;
    private String currency;

    // Статистика транзакций
    private Long totalTransactionCount;
    private Long weeklyTransactionCount;
    private Long monthlyTransactionCount;
    private Long todayTransactionCount;

    // Суммы по типам транзакций
    private BigDecimal depositSum;
    private BigDecimal withdrawSum;
    private BigDecimal purchaseSum;
    private BigDecimal refundSum;

    // Временные метрики
    private LocalDateTime lastTransactionDate;
    private LocalDateTime firstTransactionDate;
    private LocalDateTime balanceCreatedAt;
    private LocalDateTime lastBalanceUpdate;

    // Аналитические данные
    private BigDecimal averageTransactionAmount;
    private BigDecimal maxTransactionAmount;
    private BigDecimal minTransactionAmount;
    private BigDecimal monthlyTurnover;
    private BigDecimal weeklyTurnover;

    // Статистика по периодам
    private Map<String, BigDecimal> monthlyDeposits;
    private Map<String, BigDecimal> monthlySpending;
    private Map<String, Long> transactionCountByType;
    private List<Map<String, Object>> recentTransactions;

    // Дополнительные метрики
    private String balanceStatus;
    private Double healthScore; // Оценка здоровья баланса от 0 до 100
    private List<String> warnings; // Предупреждения о балансе
    private Map<String, Object> recommendations; // Рекомендации

    // Конструкторы
    public BalanceStatisticsResponse() {
    }

    public BalanceStatisticsResponse(BigDecimal currentBalance, String currency) {
        this.currentBalance = currentBalance;
        this.currency = currency;
        this.availableBalance = currentBalance;
    }

    // Getters и Setters
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

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public void setAvailableBalance(BigDecimal availableBalance) {
        this.availableBalance = availableBalance;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Long getTotalTransactionCount() {
        return totalTransactionCount;
    }

    public void setTotalTransactionCount(Long totalTransactionCount) {
        this.totalTransactionCount = totalTransactionCount;
    }

    public Long getWeeklyTransactionCount() {
        return weeklyTransactionCount;
    }

    public void setWeeklyTransactionCount(Long weeklyTransactionCount) {
        this.weeklyTransactionCount = weeklyTransactionCount;
    }

    public Long getMonthlyTransactionCount() {
        return monthlyTransactionCount;
    }

    public void setMonthlyTransactionCount(Long monthlyTransactionCount) {
        this.monthlyTransactionCount = monthlyTransactionCount;
    }

    public Long getTodayTransactionCount() {
        return todayTransactionCount;
    }

    public void setTodayTransactionCount(Long todayTransactionCount) {
        this.todayTransactionCount = todayTransactionCount;
    }

    public BigDecimal getDepositSum() {
        return depositSum;
    }

    public void setDepositSum(BigDecimal depositSum) {
        this.depositSum = depositSum;
    }

    public BigDecimal getWithdrawSum() {
        return withdrawSum;
    }

    public void setWithdrawSum(BigDecimal withdrawSum) {
        this.withdrawSum = withdrawSum;
    }

    public BigDecimal getPurchaseSum() {
        return purchaseSum;
    }

    public void setPurchaseSum(BigDecimal purchaseSum) {
        this.purchaseSum = purchaseSum;
    }

    public BigDecimal getRefundSum() {
        return refundSum;
    }

    public void setRefundSum(BigDecimal refundSum) {
        this.refundSum = refundSum;
    }

    public LocalDateTime getLastTransactionDate() {
        return lastTransactionDate;
    }

    public void setLastTransactionDate(LocalDateTime lastTransactionDate) {
        this.lastTransactionDate = lastTransactionDate;
    }

    public LocalDateTime getFirstTransactionDate() {
        return firstTransactionDate;
    }

    public void setFirstTransactionDate(LocalDateTime firstTransactionDate) {
        this.firstTransactionDate = firstTransactionDate;
    }

    public LocalDateTime getBalanceCreatedAt() {
        return balanceCreatedAt;
    }

    public void setBalanceCreatedAt(LocalDateTime balanceCreatedAt) {
        this.balanceCreatedAt = balanceCreatedAt;
    }

    public LocalDateTime getLastBalanceUpdate() {
        return lastBalanceUpdate;
    }

    public void setLastBalanceUpdate(LocalDateTime lastBalanceUpdate) {
        this.lastBalanceUpdate = lastBalanceUpdate;
    }

    public BigDecimal getAverageTransactionAmount() {
        return averageTransactionAmount;
    }

    public void setAverageTransactionAmount(BigDecimal averageTransactionAmount) {
        this.averageTransactionAmount = averageTransactionAmount;
    }

    public BigDecimal getMaxTransactionAmount() {
        return maxTransactionAmount;
    }

    public void setMaxTransactionAmount(BigDecimal maxTransactionAmount) {
        this.maxTransactionAmount = maxTransactionAmount;
    }

    public BigDecimal getMinTransactionAmount() {
        return minTransactionAmount;
    }

    public void setMinTransactionAmount(BigDecimal minTransactionAmount) {
        this.minTransactionAmount = minTransactionAmount;
    }

    public BigDecimal getMonthlyTurnover() {
        return monthlyTurnover;
    }

    public void setMonthlyTurnover(BigDecimal monthlyTurnover) {
        this.monthlyTurnover = monthlyTurnover;
    }

    public BigDecimal getWeeklyTurnover() {
        return weeklyTurnover;
    }

    public void setWeeklyTurnover(BigDecimal weeklyTurnover) {
        this.weeklyTurnover = weeklyTurnover;
    }

    public Map<String, BigDecimal> getMonthlyDeposits() {
        return monthlyDeposits;
    }

    public void setMonthlyDeposits(Map<String, BigDecimal> monthlyDeposits) {
        this.monthlyDeposits = monthlyDeposits;
    }

    public Map<String, BigDecimal> getMonthlySpending() {
        return monthlySpending;
    }

    public void setMonthlySpending(Map<String, BigDecimal> monthlySpending) {
        this.monthlySpending = monthlySpending;
    }

    public Map<String, Long> getTransactionCountByType() {
        return transactionCountByType;
    }

    public void setTransactionCountByType(Map<String, Long> transactionCountByType) {
        this.transactionCountByType = transactionCountByType;
    }

    public List<Map<String, Object>> getRecentTransactions() {
        return recentTransactions;
    }

    public void setRecentTransactions(List<Map<String, Object>> recentTransactions) {
        this.recentTransactions = recentTransactions;
    }

    public String getBalanceStatus() {
        return balanceStatus;
    }

    public void setBalanceStatus(String balanceStatus) {
        this.balanceStatus = balanceStatus;
    }

    public Double getHealthScore() {
        return healthScore;
    }

    public void setHealthScore(Double healthScore) {
        this.healthScore = healthScore;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public Map<String, Object> getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(Map<String, Object> recommendations) {
        this.recommendations = recommendations;
    }

    @Override
    public String toString() {
        return "BalanceStatisticsResponse{" +
                "currentBalance=" + currentBalance +
                ", totalDeposited=" + totalDeposited +
                ", totalSpent=" + totalSpent +
                ", reservedAmount=" + reservedAmount +
                ", availableBalance=" + availableBalance +
                ", currency='" + currency + '\'' +
                ", totalTransactionCount=" + totalTransactionCount +
                ", weeklyTransactionCount=" + weeklyTransactionCount +
                ", monthlyTransactionCount=" + monthlyTransactionCount +
                ", balanceStatus='" + balanceStatus + '\'' +
                ", healthScore=" + healthScore +
                '}';
    }
}