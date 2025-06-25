package shit.back.dto.monitoring;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PerformanceMetrics implements Serializable {
    private static final long serialVersionUID = 1L;

    private double cpuUsage;
    private double memoryUsage;
    private double responseTime;
    private long requestCount;
    private long errorCount;
    private long uptime;

    // ИСПРАВЛЕНИЕ: Добавляем поля для совместимости с JavaScript
    private double averageResponseTime; // JavaScript ожидает это поле
    private int memoryUsagePercent; // JavaScript ожидает это поле
    private int cacheHitRatio; // JavaScript ожидает это поле

    public PerformanceMetrics() {
    }

    public PerformanceMetrics(double cpuUsage, double memoryUsage, double responseTime, long requestCount,
            long errorCount, long uptime) {
        this.cpuUsage = cpuUsage;
        this.memoryUsage = memoryUsage;
        this.responseTime = responseTime;
        this.requestCount = requestCount;
        this.errorCount = errorCount;
        this.uptime = uptime;

        // ИСПРАВЛЕНИЕ: Инициализируем новые поля для совместимости
        this.averageResponseTime = responseTime; // По умолчанию равно responseTime
        this.memoryUsagePercent = (int) (memoryUsage * 100); // Конвертируем в проценты
        this.cacheHitRatio = 90; // Значение по умолчанию для cache hit ratio
    }

    public double getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(double cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public double getMemoryUsage() {
        return memoryUsage;
    }

    public void setMemoryUsage(double memoryUsage) {
        this.memoryUsage = memoryUsage;
    }

    public double getResponseTime() {
        return responseTime;
    }

    public void setResponseTime(double responseTime) {
        this.responseTime = responseTime;
    }

    public long getRequestCount() {
        return requestCount;
    }

    public void setRequestCount(long requestCount) {
        this.requestCount = requestCount;
    }

    public long getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(long errorCount) {
        this.errorCount = errorCount;
    }

    public long getUptime() {
        return uptime;
    }

    public void setUptime(long uptime) {
        this.uptime = uptime;
    }

    // ИСПРАВЛЕНИЕ: Геттеры и сеттеры для новых полей совместимости с JavaScript

    public double getAverageResponseTime() {
        return averageResponseTime;
    }

    public void setAverageResponseTime(double averageResponseTime) {
        this.averageResponseTime = averageResponseTime;
    }

    public int getMemoryUsagePercent() {
        return memoryUsagePercent;
    }

    public void setMemoryUsagePercent(int memoryUsagePercent) {
        this.memoryUsagePercent = memoryUsagePercent;
    }

    public int getCacheHitRatio() {
        return cacheHitRatio;
    }

    public void setCacheHitRatio(int cacheHitRatio) {
        this.cacheHitRatio = cacheHitRatio;
    }

    @Override
    public String toString() {
        return "PerformanceMetrics{" +
                "cpuUsage=" + cpuUsage +
                ", memoryUsage=" + memoryUsage +
                ", responseTime=" + responseTime +
                ", requestCount=" + requestCount +
                ", errorCount=" + errorCount +
                ", uptime=" + uptime +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof PerformanceMetrics))
            return false;
        PerformanceMetrics that = (PerformanceMetrics) o;
        return Double.compare(that.cpuUsage, cpuUsage) == 0
                && Double.compare(that.memoryUsage, memoryUsage) == 0
                && Double.compare(that.responseTime, responseTime) == 0
                && requestCount == that.requestCount
                && errorCount == that.errorCount
                && uptime == that.uptime
                && Double.compare(that.averageResponseTime, averageResponseTime) == 0
                && memoryUsagePercent == that.memoryUsagePercent
                && cacheHitRatio == that.cacheHitRatio;
    }

    @Override
    public int hashCode() {
        return Objects.hash(cpuUsage, memoryUsage, responseTime, requestCount, errorCount, uptime,
                averageResponseTime, memoryUsagePercent, cacheHitRatio);
    }
}
