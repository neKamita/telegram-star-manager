package shit.back.service.metrics;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Стратегия для сбора метрик производительности
 * Позволяет использовать различные подходы к сбору данных
 */
public interface MetricsCollectionStrategy {

    /**
     * Собрать метрики производительности
     */
    PerformanceMetrics collectMetrics();

    /**
     * Получить название стратегии
     */
    String getStrategyName();

    /**
     * Проверить доступность стратегии
     */
    boolean isAvailable();

    /**
     * Получить конфигурацию стратегии
     */
    Map<String, Object> getConfiguration();

    /**
     * Данные метрик производительности
     */
    record PerformanceMetrics(
            Double responseTime,
            Integer memoryUsage,
            Integer cacheHitRatio,
            Long totalUsers,
            Long activeUsers,
            Long onlineUsers,
            Long totalOrders,
            Integer healthScore,
            LocalDateTime timestamp,
            String source,
            Long collectionNumber,
            Map<String, Object> metadata,
            // Database & Cache метрики
            Integer dbPoolUsage,
            Integer cacheMissRatio,
            Integer activeDbConnections,
            // Query execution statistics - РАСШИРЕННЫЕ DATABASE МЕТРИКИ
            Double averageConnectionAcquisitionTimeMs,
            Long totalConnectionRequests,
            Long connectionLeaksDetected,
            String connectionPoolPerformanceLevel,
            Double connectionPoolEfficiency) {

        public static Builder builder() {
            return new Builder();
        }

        public Builder toBuilder() {
            return new Builder()
                    .responseTime(this.responseTime)
                    .memoryUsage(this.memoryUsage)
                    .cacheHitRatio(this.cacheHitRatio)
                    .totalUsers(this.totalUsers)
                    .activeUsers(this.activeUsers)
                    .onlineUsers(this.onlineUsers)
                    .totalOrders(this.totalOrders)
                    .healthScore(this.healthScore)
                    .timestamp(this.timestamp)
                    .source(this.source)
                    .collectionNumber(this.collectionNumber)
                    .metadata(this.metadata)
                    .dbPoolUsage(this.dbPoolUsage)
                    .cacheMissRatio(this.cacheMissRatio)
                    .activeDbConnections(this.activeDbConnections)
                    .averageConnectionAcquisitionTimeMs(this.averageConnectionAcquisitionTimeMs)
                    .totalConnectionRequests(this.totalConnectionRequests)
                    .connectionLeaksDetected(this.connectionLeaksDetected)
                    .connectionPoolPerformanceLevel(this.connectionPoolPerformanceLevel)
                    .connectionPoolEfficiency(this.connectionPoolEfficiency);
        }

        public static class Builder {
            private Double responseTime;
            private Integer memoryUsage;
            private Integer cacheHitRatio;
            private Long totalUsers;
            private Long activeUsers;
            private Long onlineUsers;
            private Long totalOrders;
            private Integer healthScore;
            private LocalDateTime timestamp;
            private String source;
            private Long collectionNumber;
            private Map<String, Object> metadata = Map.of();
            // Database & Cache метрики
            private Integer dbPoolUsage;
            private Integer cacheMissRatio;
            private Integer activeDbConnections;
            // Query execution statistics - РАСШИРЕННЫЕ DATABASE МЕТРИКИ
            private Double averageConnectionAcquisitionTimeMs;
            private Long totalConnectionRequests;
            private Long connectionLeaksDetected;
            private String connectionPoolPerformanceLevel;
            private Double connectionPoolEfficiency;

            public Builder responseTime(Double responseTime) {
                this.responseTime = responseTime;
                return this;
            }

            public Builder memoryUsage(Integer memoryUsage) {
                this.memoryUsage = memoryUsage;
                return this;
            }

            public Builder cacheHitRatio(Integer cacheHitRatio) {
                this.cacheHitRatio = cacheHitRatio;
                return this;
            }

            public Builder totalUsers(Long totalUsers) {
                this.totalUsers = totalUsers;
                return this;
            }

            public Builder activeUsers(Long activeUsers) {
                this.activeUsers = activeUsers;
                return this;
            }

            public Builder onlineUsers(Long onlineUsers) {
                this.onlineUsers = onlineUsers;
                return this;
            }

            public Builder totalOrders(Long totalOrders) {
                this.totalOrders = totalOrders;
                return this;
            }

            public Builder healthScore(Integer healthScore) {
                this.healthScore = healthScore;
                return this;
            }

            public Builder timestamp(LocalDateTime timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public Builder source(String source) {
                this.source = source;
                return this;
            }

            public Builder collectionNumber(Long collectionNumber) {
                this.collectionNumber = collectionNumber;
                return this;
            }

            public Builder metadata(Map<String, Object> metadata) {
                this.metadata = metadata != null ? metadata : Map.of();
                return this;
            }

            public Builder dbPoolUsage(Integer dbPoolUsage) {
                this.dbPoolUsage = dbPoolUsage;
                return this;
            }

            public Builder cacheMissRatio(Integer cacheMissRatio) {
                this.cacheMissRatio = cacheMissRatio;
                return this;
            }

            public Builder activeDbConnections(Integer activeDbConnections) {
                this.activeDbConnections = activeDbConnections;
                return this;
            }

            public Builder averageConnectionAcquisitionTimeMs(Double averageConnectionAcquisitionTimeMs) {
                this.averageConnectionAcquisitionTimeMs = averageConnectionAcquisitionTimeMs;
                return this;
            }

            public Builder totalConnectionRequests(Long totalConnectionRequests) {
                this.totalConnectionRequests = totalConnectionRequests;
                return this;
            }

            public Builder connectionLeaksDetected(Long connectionLeaksDetected) {
                this.connectionLeaksDetected = connectionLeaksDetected;
                return this;
            }

            public Builder connectionPoolPerformanceLevel(String connectionPoolPerformanceLevel) {
                this.connectionPoolPerformanceLevel = connectionPoolPerformanceLevel;
                return this;
            }

            public Builder connectionPoolEfficiency(Double connectionPoolEfficiency) {
                this.connectionPoolEfficiency = connectionPoolEfficiency;
                return this;
            }

            public PerformanceMetrics build() {
                return new PerformanceMetrics(
                        responseTime,
                        memoryUsage,
                        cacheHitRatio,
                        totalUsers,
                        activeUsers,
                        onlineUsers,
                        totalOrders,
                        healthScore,
                        timestamp,
                        source,
                        collectionNumber,
                        metadata,
                        dbPoolUsage,
                        cacheMissRatio,
                        activeDbConnections,
                        averageConnectionAcquisitionTimeMs,
                        totalConnectionRequests,
                        connectionLeaksDetected,
                        connectionPoolPerformanceLevel,
                        connectionPoolEfficiency);
            }
        }
    }
}