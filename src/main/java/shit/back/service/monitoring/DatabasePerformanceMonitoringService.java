package shit.back.service.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import shit.back.service.activity.HighPerformanceUserActivityService;
import shit.back.service.OptimizedUserSessionService;
import shit.back.application.balance.service.HighPerformanceBalanceService;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * КРИТИЧЕСКИ ВАЖНЫЙ сервис мониторинга производительности БД
 * 
 * ФУНКЦИИ МОНИТОРИНГА:
 * 1. Отслеживание времени выполнения DB операций
 * 2. Мониторинг cache hit ratio для всех сервисов
 * 3. Выявление медленных операций (>50ms)
 * 4. Статистика batch операций и их эффективности
 * 5. Уведомления о критических проблемах производительности
 * 
 * ЦЕЛЕВЫЕ ПОКАЗАТЕЛИ:
 * - DB операции: <50ms (цель <30ms для критических)
 * - Cache hit ratio: >90%
 * - Batch throughput: +300% по сравнению с single операциями
 * - Alert threshold: >100ms для любой операции
 * 
 * Принципы: SOLID, DRY, Clean Code, KISS, Fail-Fast, YAGNI
 */
@Slf4j
@Service
public class DatabasePerformanceMonitoringService {

    @Autowired(required = false)
    private HighPerformanceUserActivityService activityService;

    @Autowired(required = false)
    private OptimizedUserSessionService sessionService;

    @Autowired(required = false)
    private HighPerformanceBalanceService balanceService;

    // Глобальные метрики производительности
    private final AtomicLong totalDbOperations = new AtomicLong(0);
    private final AtomicLong totalDbTime = new AtomicLong(0);
    private final AtomicInteger slowOperationsCount = new AtomicInteger(0);
    private final AtomicInteger criticalOperationsCount = new AtomicInteger(0);

    // Пороги производительности
    private static final long SLOW_OPERATION_THRESHOLD_MS = 50;
    private static final long CRITICAL_OPERATION_THRESHOLD_MS = 100;
    private static final long TARGET_OPERATION_THRESHOLD_MS = 30;
    private static final double TARGET_CACHE_HIT_RATIO = 90.0;

    /**
     * КРИТИЧЕСКИЙ МОНИТОРИНГ: Запланированный отчет о производительности
     */
    @Scheduled(fixedRate = 60000) // каждую минуту
    public void monitorDatabasePerformance() {
        try {
            long startTime = System.currentTimeMillis();

            log.info("🔍 === МОНИТОРИНГ ПРОИЗВОДИТЕЛЬНОСТИ БД ===");

            // Мониторинг сервиса активности
            if (activityService != null) {
                monitorActivityServicePerformance();
            }

            // Мониторинг сервиса сессий
            if (sessionService != null) {
                monitorSessionServicePerformance();
            }

            // Мониторинг сервиса баланса
            if (balanceService != null) {
                monitorBalanceServicePerformance();
            }

            // Общая статистика
            logOverallPerformanceStats();

            long monitoringDuration = System.currentTimeMillis() - startTime;
            log.debug("📊 Мониторинг завершен за {}ms", monitoringDuration);

        } catch (Exception e) {
            log.error("🚨 ОШИБКА МОНИТОРИНГА: {}", e.getMessage(), e);
        }
    }

    /**
     * Мониторинг производительности сервиса активности
     */
    private void monitorActivityServicePerformance() {
        try {
            var metrics = activityService.getPerformanceMetrics();

            log.info("📊 ACTIVITY SERVICE:");
            log.info("   Обработано записей: {}", metrics.totalProcessed);
            log.info("   Batch операций: {}", metrics.batchCount);
            log.info("   Размер буфера: {}/{}", metrics.currentBufferSize, metrics.maxBufferSize);
            log.info("   Порог производительности: {}ms", metrics.performanceThresholdMs);

            // Предупреждения
            if (metrics.currentBufferSize > metrics.maxBufferSize * 0.8) {
                log.warn("⚠️ ACTIVITY: Буфер заполнен на {}%",
                        (double) metrics.currentBufferSize / metrics.maxBufferSize * 100);
            }

            // Расчет эффективности batch операций
            if (metrics.batchCount > 0) {
                double avgBatchSize = (double) metrics.totalProcessed / metrics.batchCount;
                log.info("   Средний размер batch: {:.1f}", avgBatchSize);

                if (avgBatchSize < metrics.optimalBatchSize * 0.5) {
                    log.warn("⚠️ ACTIVITY: Низкая эффективность batch операций (avg={:.1f}, optimal={})",
                            avgBatchSize, metrics.optimalBatchSize);
                }
            }

        } catch (Exception e) {
            log.error("🚨 ACTIVITY MONITORING ERROR: {}", e.getMessage(), e);
        }
    }

    /**
     * Мониторинг производительности сервиса сессий
     */
    private void monitorSessionServicePerformance() {
        try {
            var metrics = sessionService.getPerformanceMetrics();

            log.info("📊 SESSION SERVICE:");
            log.info("   Cache hits: {}", metrics.cacheHits);
            log.info("   Cache misses: {}", metrics.cacheMisses);
            log.info("   Hit ratio: {:.1f}%", metrics.hitRatio);
            log.info("   Cache размер: {}/{}", metrics.cacheSize, metrics.maxCacheSize);
            log.info("   DB операций: {}", metrics.dbOperations);

            // Критические предупреждения
            if (metrics.hitRatio < TARGET_CACHE_HIT_RATIO) {
                log.error("🚨 SESSION: КРИТИЧЕСКИ НИЗКИЙ hit ratio {:.1f}% (цель: >{}%)",
                        metrics.hitRatio, TARGET_CACHE_HIT_RATIO);
            }

            if (metrics.cacheSize > metrics.maxCacheSize * 0.9) {
                log.warn("⚠️ SESSION: Cache почти заполнен на {}%",
                        (double) metrics.cacheSize / metrics.maxCacheSize * 100);
            }

        } catch (Exception e) {
            log.error("🚨 SESSION MONITORING ERROR: {}", e.getMessage(), e);
        }
    }

    /**
     * Мониторинг производительности сервиса баланса
     */
    private void monitorBalanceServicePerformance() {
        try {
            var metrics = balanceService.getPerformanceMetrics();

            log.info("📊 BALANCE SERVICE:");
            log.info("   Cache hits: {}", metrics.cacheHits);
            log.info("   Cache misses: {}", metrics.cacheMisses);
            log.info("   Hit ratio: {:.1f}%", metrics.hitRatio);
            log.info("   Cache размер: {}/{}", metrics.cacheSize, metrics.maxCacheSize);
            log.info("   DB запросов: {}", metrics.dbQueries);
            log.info("   Всего операций: {}", metrics.totalOperations);
            log.info("   Среднее время отклика: {:.1f}ms", metrics.avgResponseTime);

            // Критические предупреждения
            if (metrics.hitRatio < TARGET_CACHE_HIT_RATIO) {
                log.error("🚨 BALANCE: КРИТИЧЕСКИ НИЗКИЙ hit ratio {:.1f}% (цель: >{}%)",
                        metrics.hitRatio, TARGET_CACHE_HIT_RATIO);
            }

            if (metrics.avgResponseTime > TARGET_OPERATION_THRESHOLD_MS) {
                log.warn("⚠️ BALANCE: Среднее время отклика {:.1f}ms превышает цель {}ms",
                        metrics.avgResponseTime, TARGET_OPERATION_THRESHOLD_MS);
            }

            if (metrics.avgResponseTime > CRITICAL_OPERATION_THRESHOLD_MS) {
                log.error("🚨 BALANCE: КРИТИЧЕСКИ МЕДЛЕННЫЕ операции! Среднее время: {:.1f}ms",
                        metrics.avgResponseTime);
                criticalOperationsCount.incrementAndGet();
            }

        } catch (Exception e) {
            log.error("🚨 BALANCE MONITORING ERROR: {}", e.getMessage(), e);
        }
    }

    /**
     * Общая статистика производительности
     */
    private void logOverallPerformanceStats() {
        long totalOps = totalDbOperations.get();
        long totalTime = totalDbTime.get();
        int slowOps = slowOperationsCount.get();
        int criticalOps = criticalOperationsCount.get();

        double avgTime = totalOps > 0 ? (double) totalTime / totalOps : 0;
        double slowOpsPercent = totalOps > 0 ? (double) slowOps / totalOps * 100 : 0;
        double criticalOpsPercent = totalOps > 0 ? (double) criticalOps / totalOps * 100 : 0;

        log.info("📊 === ОБЩАЯ СТАТИСТИКА ПРОИЗВОДИТЕЛЬНОСТИ ===");
        log.info("   Всего DB операций: {}", totalOps);
        log.info("   Среднее время операции: {:.1f}ms", avgTime);
        log.info("   Медленных операций (>{}ms): {} ({:.1f}%)",
                SLOW_OPERATION_THRESHOLD_MS, slowOps, slowOpsPercent);
        log.info("   Критических операций (>{}ms): {} ({:.1f}%)",
                CRITICAL_OPERATION_THRESHOLD_MS, criticalOps, criticalOpsPercent);

        // Критические предупреждения
        if (avgTime > TARGET_OPERATION_THRESHOLD_MS) {
            log.warn("⚠️ ОБЩЕЕ: Среднее время операций {:.1f}ms превышает цель {}ms",
                    avgTime, TARGET_OPERATION_THRESHOLD_MS);
        }

        if (criticalOpsPercent > 5.0) {
            log.error("🚨 КРИТИЧЕСКАЯ ПРОБЛЕМА: {}% операций превышают {}ms!",
                    criticalOpsPercent, CRITICAL_OPERATION_THRESHOLD_MS);
        }

        // Успешные метрики
        if (avgTime <= TARGET_OPERATION_THRESHOLD_MS && criticalOpsPercent < 1.0) {
            log.info("✅ ОТЛИЧНАЯ ПРОИЗВОДИТЕЛЬНОСТЬ: Среднее время {:.1f}ms, критических операций {:.1f}%",
                    avgTime, criticalOpsPercent);
        }
    }

    /**
     * Регистрация операции БД для статистики
     */
    public void recordDatabaseOperation(long durationMs) {
        totalDbOperations.incrementAndGet();
        totalDbTime.addAndGet(durationMs);

        if (durationMs > SLOW_OPERATION_THRESHOLD_MS) {
            slowOperationsCount.incrementAndGet();
            log.warn("⚠️ МЕДЛЕННАЯ ОПЕРАЦИЯ: {}ms (порог: {}ms)", durationMs, SLOW_OPERATION_THRESHOLD_MS);
        }

        if (durationMs > CRITICAL_OPERATION_THRESHOLD_MS) {
            criticalOperationsCount.incrementAndGet();
            log.error("🚨 КРИТИЧЕСКИ МЕДЛЕННАЯ ОПЕРАЦИЯ: {}ms (порог: {}ms)",
                    durationMs, CRITICAL_OPERATION_THRESHOLD_MS);
        }
    }

    /**
     * Получить текущие метрики производительности
     */
    public OverallPerformanceMetrics getOverallMetrics() {
        long totalOps = totalDbOperations.get();
        long totalTime = totalDbTime.get();

        double avgTime = totalOps > 0 ? (double) totalTime / totalOps : 0;
        double slowOpsPercent = totalOps > 0 ? (double) slowOperationsCount.get() / totalOps * 100 : 0;
        double criticalOpsPercent = totalOps > 0 ? (double) criticalOperationsCount.get() / totalOps * 100 : 0;

        return new OverallPerformanceMetrics(
                totalOps, avgTime,
                slowOperationsCount.get(), slowOpsPercent,
                criticalOperationsCount.get(), criticalOpsPercent,
                TARGET_OPERATION_THRESHOLD_MS, CRITICAL_OPERATION_THRESHOLD_MS);
    }

    /**
     * Сброс статистики (для тестирования)
     */
    public void resetStatistics() {
        totalDbOperations.set(0);
        totalDbTime.set(0);
        slowOperationsCount.set(0);
        criticalOperationsCount.set(0);
        log.info("📊 СТАТИСТИКА СБРОШЕНА");
    }

    /**
     * Метрики общей производительности
     */
    public static class OverallPerformanceMetrics {
        public final long totalOperations;
        public final double avgResponseTime;
        public final int slowOperations;
        public final double slowOperationsPercent;
        public final int criticalOperations;
        public final double criticalOperationsPercent;
        public final long targetThresholdMs;
        public final long criticalThresholdMs;

        public OverallPerformanceMetrics(long totalOperations, double avgResponseTime,
                int slowOperations, double slowOperationsPercent,
                int criticalOperations, double criticalOperationsPercent,
                long targetThresholdMs, long criticalThresholdMs) {
            this.totalOperations = totalOperations;
            this.avgResponseTime = avgResponseTime;
            this.slowOperations = slowOperations;
            this.slowOperationsPercent = slowOperationsPercent;
            this.criticalOperations = criticalOperations;
            this.criticalOperationsPercent = criticalOperationsPercent;
            this.targetThresholdMs = targetThresholdMs;
            this.criticalThresholdMs = criticalThresholdMs;
        }

        @Override
        public String toString() {
            return String.format(
                    "OverallMetrics{ops=%d, avgTime=%.1fms, slow=%d(%.1f%%), critical=%d(%.1f%%), target=%dms, criticalThreshold=%dms}",
                    totalOperations, avgResponseTime, slowOperations, slowOperationsPercent,
                    criticalOperations, criticalOperationsPercent, targetThresholdMs, criticalThresholdMs);
        }
    }
}