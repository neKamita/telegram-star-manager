package shit.back.application.service.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import shit.back.service.AdminDashboardCacheService;
import shit.back.service.metrics.MetricsCollectionStrategy;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Координирующий сервис для метрик
 * Заменяет BackgroundMetricsService и оркестрирует работу всех metrics сервисов
 * Следует принципам SOLID - единая ответственность за координацию
 */
@Service
public class MetricsCoordinatorService {

    private static final Logger log = LoggerFactory.getLogger(MetricsCoordinatorService.class);

    @Autowired
    private MetricsCollectionService metricsCollectionService;

    @Autowired
    private MetricsValidationService metricsValidationService;

    @Autowired
    private MetricsDataFormatterService dataFormatterService;

    @Autowired
    private MetricsSSEBroadcastService sseBroadcastService;

    @Autowired
    private AdminDashboardCacheService cacheService;

    // Performance tracking
    private final AtomicLong metricsCollectionCount = new AtomicLong(0);
    private final AtomicLong lastCollectionDuration = new AtomicLong(0);
    private volatile LocalDateTime lastSuccessfulCollection;
    private volatile Object lastMetrics;

    /**
     * Основной scheduled метод для сбора и broadcast метрик каждые 15 секунд
     */
    @Scheduled(fixedRate = 15000) // 15 секунд - оптимальный баланс
    @Async("metricsBackgroundExecutor")
    public void collectAndBroadcastMetrics() {
        long startTime = System.currentTimeMillis();
        long collectionNumber = metricsCollectionCount.incrementAndGet();

        try {
            log.info("🚀 Background metrics collection #{} started at {} (interval: 15 seconds)",
                    collectionNumber, LocalDateTime.now());
            log.info("🔍 Active SSE connections: {}",
                    sseBroadcastService.getSSEStatistics().getActiveConnections());

            // Собираем и валидируем метрики
            PerformanceMetricsData metrics = collectAndValidateMetrics();

            if (metrics != null) {
                // Кэшируем результат
                cacheMetrics(metrics);

                // Broadcast через SSE
                sseBroadcastService.broadcastMetrics(metrics);

                // Обновляем статистику
                updateCollectionStatistics(startTime, metrics);

                log.info(
                        "✅ Background metrics collection #{} completed in {}ms (CACHED DATA ONLY), broadcasted to {} connections",
                        collectionNumber, lastCollectionDuration.get(),
                        sseBroadcastService.getSSEStatistics().getActiveConnections());
            } else {
                log.warn("⚠️ Метрики равны null, отправляем fallback");
                handleFailedCollection(startTime, collectionNumber);
            }

        } catch (Exception e) {
            log.error("❌ Background metrics collection #{} failed: {}", collectionNumber, e.getMessage(), e);
            handleFailedCollection(startTime, collectionNumber);
        }
    }

    /**
     * Сбор и валидация метрик через все сервисы
     */
    private PerformanceMetricsData collectAndValidateMetrics() {
        try {
            log.debug("🔍 Сбор метрик через стратегию...");

            // Собираем метрики через collection service
            MetricsCollectionStrategy.PerformanceMetrics strategyMetrics = metricsCollectionService.collectMetrics();

            if (strategyMetrics == null) {
                log.warn("⚠️ Стратегия вернула null метрики");
                return createFallbackMetricsData();
            }

            // Валидируем через validation service
            return validateAndBuildMetrics(strategyMetrics);

        } catch (Exception e) {
            log.error("❌ Ошибка при сборе метрик: {}", e.getMessage(), e);
            return createFallbackMetricsData();
        }
    }

    /**
     * Валидация и построение финальных метрик
     */
    private PerformanceMetricsData validateAndBuildMetrics(
            MetricsCollectionStrategy.PerformanceMetrics strategyMetrics) {

        try {
            // Валидируем cache метрики
            MetricsValidationService.CacheMetrics cacheMetrics = metricsValidationService.validateAndFixCacheMetrics(
                    null, strategyMetrics.cacheMissRatio());

            // Валидируем database pool метрики
            MetricsValidationService.DatabasePoolMetrics dbMetrics = metricsValidationService
                    .validateAndGetDatabasePoolMetrics();

            // Валидируем расширенные connection pool метрики
            MetricsValidationService.ExtendedConnectionPoolMetrics extendedMetrics = metricsValidationService
                    .validateExtendedConnectionPoolMetrics(
                            strategyMetrics.averageConnectionAcquisitionTimeMs(),
                            strategyMetrics.totalConnectionRequests(),
                            strategyMetrics.connectionLeaksDetected(),
                            strategyMetrics.connectionPoolPerformanceLevel(),
                            strategyMetrics.connectionPoolEfficiency());

            // Валидируем системные метрики
            MetricsValidationService.SystemMetrics systemMetrics = metricsValidationService.validateSystemMetrics(
                    strategyMetrics.responseTime(),
                    metricsCollectionService.calculateMemoryUsage(),
                    strategyMetrics.totalUsers(),
                    strategyMetrics.activeUsers(),
                    strategyMetrics.onlineUsers(),
                    strategyMetrics.totalOrders(),
                    strategyMetrics.healthScore());

            // Строим финальные метрики
            return PerformanceMetricsData.builder()
                    .responseTime(systemMetrics.getResponseTime())
                    .memoryUsage(systemMetrics.getMemoryUsage())
                    .cacheHitRatio(cacheMetrics.getHitRatio())
                    .cacheMissRatio(cacheMetrics.getMissRatio())
                    .dbPoolUsage(dbMetrics.getUtilization())
                    .activeDbConnections(dbMetrics.getActiveConnections())
                    .averageConnectionAcquisitionTimeMs(extendedMetrics.getAvgAcquisitionTime())
                    .totalConnectionRequests(extendedMetrics.getTotalRequests())
                    .connectionLeaksDetected(extendedMetrics.getLeaksDetected())
                    .connectionPoolPerformanceLevel(extendedMetrics.getPerformanceLevel())
                    .connectionPoolEfficiency(extendedMetrics.getEfficiency())
                    .totalUsers(systemMetrics.getTotalUsers())
                    .activeUsers(systemMetrics.getActiveUsers())
                    .onlineUsers(systemMetrics.getOnlineUsers())
                    .totalOrders(systemMetrics.getTotalOrders())
                    .healthScore(systemMetrics.getHealthScore())
                    .timestamp(LocalDateTime.now())
                    .source("coordinator-service-strategy")
                    .collectionNumber(metricsCollectionCount.get())
                    .build();

        } catch (Exception e) {
            log.error("❌ Ошибка валидации метрик: {}", e.getMessage(), e);
            return createFallbackMetricsData();
        }
    }

    /**
     * Создание fallback метрик
     */
    private PerformanceMetricsData createFallbackMetricsData() {
        try {
            MetricsCollectionService.MetricsData fallbackData = metricsCollectionService.createFallbackMetrics();

            return PerformanceMetricsData.builder()
                    .responseTime(fallbackData.getResponseTime())
                    .memoryUsage(fallbackData.getMemoryUsage())
                    .cacheHitRatio(fallbackData.getCacheHitRatio())
                    .cacheMissRatio(fallbackData.getCacheMissRatio())
                    .dbPoolUsage(fallbackData.getDbPoolUsage())
                    .activeDbConnections(fallbackData.getActiveDbConnections())
                    .averageConnectionAcquisitionTimeMs(fallbackData.getAverageConnectionAcquisitionTimeMs())
                    .totalConnectionRequests(fallbackData.getTotalConnectionRequests())
                    .connectionLeaksDetected(fallbackData.getConnectionLeaksDetected())
                    .connectionPoolPerformanceLevel(fallbackData.getConnectionPoolPerformanceLevel())
                    .connectionPoolEfficiency(fallbackData.getConnectionPoolEfficiency())
                    .totalUsers(fallbackData.getTotalUsers())
                    .activeUsers(fallbackData.getActiveUsers())
                    .onlineUsers(fallbackData.getOnlineUsers())
                    .totalOrders(fallbackData.getTotalOrders())
                    .healthScore(fallbackData.getHealthScore())
                    .timestamp(LocalDateTime.now())
                    .source("coordinator-service-fallback")
                    .collectionNumber(metricsCollectionCount.get())
                    .build();

        } catch (Exception e) {
            log.error("❌ Критическая ошибка создания fallback метрик: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Кэширование метрик
     */
    private void cacheMetrics(PerformanceMetricsData metrics) {
        try {
            log.debug("📦 Кэширование метрик производительности");
            // TODO: Расширить AdminDashboardCacheService для real-time метрик при
            // необходимости
        } catch (Exception e) {
            log.warn("Ошибка кэширования метрик: {}", e.getMessage());
        }
    }

    /**
     * Обработка неудачного сбора метрик
     */
    private void handleFailedCollection(long startTime, long collectionNumber) {
        long duration = System.currentTimeMillis() - startTime;
        lastCollectionDuration.set(duration);

        log.warn("🔄 Отправка fallback метрик из-за ошибки сбора");
        if (lastMetrics != null) {
            sseBroadcastService.broadcastFallbackMetrics(lastMetrics);
        }
    }

    /**
     * Обновление статистики сбора
     */
    private void updateCollectionStatistics(long startTime, PerformanceMetricsData metrics) {
        long duration = System.currentTimeMillis() - startTime;
        lastCollectionDuration.set(duration);
        lastSuccessfulCollection = LocalDateTime.now();
        lastMetrics = metrics;
    }

    /**
     * Получение статистики coordinator service
     */
    public CoordinatorStatistics getCoordinatorStatistics() {
        return CoordinatorStatistics.builder()
                .totalCollections(metricsCollectionCount.get())
                .lastCollectionDuration(lastCollectionDuration.get())
                .lastSuccessfulCollection(lastSuccessfulCollection)
                .sseStatistics(sseBroadcastService.getSSEStatistics())
                .isHealthy(isHealthy())
                .build();
    }

    /**
     * Проверка здоровья coordinator service
     */
    public boolean isHealthy() {
        return lastSuccessfulCollection != null &&
                lastSuccessfulCollection.isAfter(LocalDateTime.now().minusSeconds(60)) &&
                sseBroadcastService.isHealthy();
    }

    // ==================== DATA CLASSES ====================

    /**
     * Финальные метрики производительности
     */
    public static class PerformanceMetricsData {
        private final Double responseTime;
        private final Integer memoryUsage;
        private final Integer cacheHitRatio;
        private final Integer cacheMissRatio;
        private final Integer dbPoolUsage;
        private final Integer activeDbConnections;
        private final Double averageConnectionAcquisitionTimeMs;
        private final Long totalConnectionRequests;
        private final Long connectionLeaksDetected;
        private final String connectionPoolPerformanceLevel;
        private final Double connectionPoolEfficiency;
        private final Long totalUsers;
        private final Long activeUsers;
        private final Long onlineUsers;
        private final Long totalOrders;
        private final Integer healthScore;
        private final LocalDateTime timestamp;
        private final String source;
        private final Long collectionNumber;

        public static PerformanceMetricsDataBuilder builder() {
            return new PerformanceMetricsDataBuilder();
        }

        private PerformanceMetricsData(PerformanceMetricsDataBuilder builder) {
            this.responseTime = builder.responseTime;
            this.memoryUsage = builder.memoryUsage;
            this.cacheHitRatio = builder.cacheHitRatio;
            this.cacheMissRatio = builder.cacheMissRatio;
            this.dbPoolUsage = builder.dbPoolUsage;
            this.activeDbConnections = builder.activeDbConnections;
            this.averageConnectionAcquisitionTimeMs = builder.averageConnectionAcquisitionTimeMs;
            this.totalConnectionRequests = builder.totalConnectionRequests;
            this.connectionLeaksDetected = builder.connectionLeaksDetected;
            this.connectionPoolPerformanceLevel = builder.connectionPoolPerformanceLevel;
            this.connectionPoolEfficiency = builder.connectionPoolEfficiency;
            this.totalUsers = builder.totalUsers;
            this.activeUsers = builder.activeUsers;
            this.onlineUsers = builder.onlineUsers;
            this.totalOrders = builder.totalOrders;
            this.healthScore = builder.healthScore;
            this.timestamp = builder.timestamp;
            this.source = builder.source;
            this.collectionNumber = builder.collectionNumber;
        }

        // Getters
        public Double getResponseTime() {
            return responseTime;
        }

        public Integer getMemoryUsage() {
            return memoryUsage;
        }

        public Integer getCacheHitRatio() {
            return cacheHitRatio;
        }

        public Integer getCacheMissRatio() {
            return cacheMissRatio;
        }

        public Integer getDbPoolUsage() {
            return dbPoolUsage;
        }

        public Integer getActiveDbConnections() {
            return activeDbConnections;
        }

        public Double getAverageConnectionAcquisitionTimeMs() {
            return averageConnectionAcquisitionTimeMs;
        }

        public Long getTotalConnectionRequests() {
            return totalConnectionRequests;
        }

        public Long getConnectionLeaksDetected() {
            return connectionLeaksDetected;
        }

        public String getConnectionPoolPerformanceLevel() {
            return connectionPoolPerformanceLevel;
        }

        public Double getConnectionPoolEfficiency() {
            return connectionPoolEfficiency;
        }

        public Long getTotalUsers() {
            return totalUsers;
        }

        public Long getActiveUsers() {
            return activeUsers;
        }

        public Long getOnlineUsers() {
            return onlineUsers;
        }

        public Long getTotalOrders() {
            return totalOrders;
        }

        public Integer getHealthScore() {
            return healthScore;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public String getSource() {
            return source;
        }

        public Long getCollectionNumber() {
            return collectionNumber;
        }

        public static class PerformanceMetricsDataBuilder {
            private Double responseTime;
            private Integer memoryUsage;
            private Integer cacheHitRatio;
            private Integer cacheMissRatio;
            private Integer dbPoolUsage;
            private Integer activeDbConnections;
            private Double averageConnectionAcquisitionTimeMs;
            private Long totalConnectionRequests;
            private Long connectionLeaksDetected;
            private String connectionPoolPerformanceLevel;
            private Double connectionPoolEfficiency;
            private Long totalUsers;
            private Long activeUsers;
            private Long onlineUsers;
            private Long totalOrders;
            private Integer healthScore;
            private LocalDateTime timestamp;
            private String source;
            private Long collectionNumber;

            public PerformanceMetricsDataBuilder responseTime(Double responseTime) {
                this.responseTime = responseTime;
                return this;
            }

            public PerformanceMetricsDataBuilder memoryUsage(Integer memoryUsage) {
                this.memoryUsage = memoryUsage;
                return this;
            }

            public PerformanceMetricsDataBuilder cacheHitRatio(Integer cacheHitRatio) {
                this.cacheHitRatio = cacheHitRatio;
                return this;
            }

            public PerformanceMetricsDataBuilder cacheMissRatio(Integer cacheMissRatio) {
                this.cacheMissRatio = cacheMissRatio;
                return this;
            }

            public PerformanceMetricsDataBuilder dbPoolUsage(Integer dbPoolUsage) {
                this.dbPoolUsage = dbPoolUsage;
                return this;
            }

            public PerformanceMetricsDataBuilder activeDbConnections(Integer activeDbConnections) {
                this.activeDbConnections = activeDbConnections;
                return this;
            }

            public PerformanceMetricsDataBuilder averageConnectionAcquisitionTimeMs(
                    Double averageConnectionAcquisitionTimeMs) {
                this.averageConnectionAcquisitionTimeMs = averageConnectionAcquisitionTimeMs;
                return this;
            }

            public PerformanceMetricsDataBuilder totalConnectionRequests(Long totalConnectionRequests) {
                this.totalConnectionRequests = totalConnectionRequests;
                return this;
            }

            public PerformanceMetricsDataBuilder connectionLeaksDetected(Long connectionLeaksDetected) {
                this.connectionLeaksDetected = connectionLeaksDetected;
                return this;
            }

            public PerformanceMetricsDataBuilder connectionPoolPerformanceLevel(String connectionPoolPerformanceLevel) {
                this.connectionPoolPerformanceLevel = connectionPoolPerformanceLevel;
                return this;
            }

            public PerformanceMetricsDataBuilder connectionPoolEfficiency(Double connectionPoolEfficiency) {
                this.connectionPoolEfficiency = connectionPoolEfficiency;
                return this;
            }

            public PerformanceMetricsDataBuilder totalUsers(Long totalUsers) {
                this.totalUsers = totalUsers;
                return this;
            }

            public PerformanceMetricsDataBuilder activeUsers(Long activeUsers) {
                this.activeUsers = activeUsers;
                return this;
            }

            public PerformanceMetricsDataBuilder onlineUsers(Long onlineUsers) {
                this.onlineUsers = onlineUsers;
                return this;
            }

            public PerformanceMetricsDataBuilder totalOrders(Long totalOrders) {
                this.totalOrders = totalOrders;
                return this;
            }

            public PerformanceMetricsDataBuilder healthScore(Integer healthScore) {
                this.healthScore = healthScore;
                return this;
            }

            public PerformanceMetricsDataBuilder timestamp(LocalDateTime timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public PerformanceMetricsDataBuilder source(String source) {
                this.source = source;
                return this;
            }

            public PerformanceMetricsDataBuilder collectionNumber(Long collectionNumber) {
                this.collectionNumber = collectionNumber;
                return this;
            }

            public PerformanceMetricsData build() {
                return new PerformanceMetricsData(this);
            }
        }
    }

    /**
     * Статистика coordinator service
     */
    public static class CoordinatorStatistics {
        private final Long totalCollections;
        private final Long lastCollectionDuration;
        private final LocalDateTime lastSuccessfulCollection;
        private final MetricsSSEBroadcastService.SSEStatistics sseStatistics;
        private final Boolean isHealthy;

        public static CoordinatorStatisticsBuilder builder() {
            return new CoordinatorStatisticsBuilder();
        }

        private CoordinatorStatistics(CoordinatorStatisticsBuilder builder) {
            this.totalCollections = builder.totalCollections;
            this.lastCollectionDuration = builder.lastCollectionDuration;
            this.lastSuccessfulCollection = builder.lastSuccessfulCollection;
            this.sseStatistics = builder.sseStatistics;
            this.isHealthy = builder.isHealthy;
        }

        // Getters
        public Long getTotalCollections() {
            return totalCollections;
        }

        public Long getLastCollectionDuration() {
            return lastCollectionDuration;
        }

        public LocalDateTime getLastSuccessfulCollection() {
            return lastSuccessfulCollection;
        }

        public MetricsSSEBroadcastService.SSEStatistics getSseStatistics() {
            return sseStatistics;
        }

        public Boolean getIsHealthy() {
            return isHealthy;
        }

        public static class CoordinatorStatisticsBuilder {
            private Long totalCollections;
            private Long lastCollectionDuration;
            private LocalDateTime lastSuccessfulCollection;
            private MetricsSSEBroadcastService.SSEStatistics sseStatistics;
            private Boolean isHealthy;

            public CoordinatorStatisticsBuilder totalCollections(Long totalCollections) {
                this.totalCollections = totalCollections;
                return this;
            }

            public CoordinatorStatisticsBuilder lastCollectionDuration(Long lastCollectionDuration) {
                this.lastCollectionDuration = lastCollectionDuration;
                return this;
            }

            public CoordinatorStatisticsBuilder lastSuccessfulCollection(LocalDateTime lastSuccessfulCollection) {
                this.lastSuccessfulCollection = lastSuccessfulCollection;
                return this;
            }

            public CoordinatorStatisticsBuilder sseStatistics(MetricsSSEBroadcastService.SSEStatistics sseStatistics) {
                this.sseStatistics = sseStatistics;
                return this;
            }

            public CoordinatorStatisticsBuilder isHealthy(Boolean isHealthy) {
                this.isHealthy = isHealthy;
                return this;
            }

            public CoordinatorStatistics build() {
                return new CoordinatorStatistics(this);
            }
        }
    }
}