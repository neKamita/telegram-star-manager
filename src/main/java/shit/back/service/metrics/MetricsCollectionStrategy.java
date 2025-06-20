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
            Map<String, Object> metadata) {

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
                    .metadata(this.metadata);
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
                        metadata);
            }
        }
    }
}