package shit.back.service.activity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shit.back.entity.OrderEntity;
import shit.back.entity.UserActivityLogEntity;
import shit.back.entity.UserActivityLogEntity.ActionType;
import shit.back.entity.UserActivityLogEntity.LogCategory;
import shit.back.entity.UserSessionEntity;
import shit.back.repository.UserActivityLogJpaRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * КРИТИЧЕСКИ ОПТИМИЗИРОВАННЫЙ сервис логирования активности пользователей
 * 
 * КЛЮЧЕВЫЕ ОПТИМИЗАЦИИ:
 * 1. Batch операции - сокращение количества SQL запросов с N до 1
 * 2. Асинхронная буферизация - накопление записей перед сохранением
 * 3. Оптимизированные размеры batch'ей для PostgreSQL
 * 4. Минимизация времени блокировки транзакций
 * 5. Fail-Fast принцип - быстрое выявление проблем
 * 
 * ЦЕЛЕВЫЕ ПОКАЗАТЕЛИ:
 * - DB операции: с 180-257ms до <30ms
 * - Throughput: +300% операций в секунду
 * - Memory usage: оптимизированные буферы
 * 
 * Принципы: SOLID, DRY, Clean Code, KISS, Fail-Fast, YAGNI
 */
@Slf4j
@Service
@Transactional
public class HighPerformanceUserActivityService {

    @Autowired
    private UserActivityLogJpaRepository activityLogRepository;

    @Autowired
    private UserActivitySSEService sseService;

    // Оптимизированные параметры batch операций
    private static final int OPTIMAL_BATCH_SIZE = 20;
    private static final int MAX_BUFFER_SIZE = 100;
    private static final int FLUSH_INTERVAL_MS = 2000; // 2 секунды
    private static final int PERFORMANCE_THRESHOLD_MS = 30;

    // Высокопроизводительные буферы
    private final BlockingQueue<UserActivityLogEntity> activityBuffer = new LinkedBlockingQueue<>(MAX_BUFFER_SIZE);
    private final AtomicInteger bufferSize = new AtomicInteger(0);
    private final AtomicInteger totalProcessed = new AtomicInteger(0);
    private final AtomicInteger batchCount = new AtomicInteger(0);

    /**
     * ВЫСОКОПРОИЗВОДИТЕЛЬНОЕ логирование активности заказа
     * Целевое время: <30ms вместо 180-257ms
     */
    @Async("userActivityLoggingExecutor")
    public void logOrderActivityOptimized(Long userId, String username, String firstName, String lastName,
            ActionType actionType, String actionDescription,
            String orderId, BigDecimal orderAmount, Integer starCount, String paymentMethod) {

        long startTime = System.currentTimeMillis();

        try {
            UserActivityLogEntity activity = new UserActivityLogEntity(
                    userId, username, firstName, lastName, actionType, actionDescription)
                    .withOrderInfo(orderId, orderAmount, starCount)
                    .withPaymentMethod(paymentMethod);

            // Высокопроизводительная буферизация
            if (addToOptimizedBuffer(activity)) {
                long duration = System.currentTimeMillis() - startTime;

                if (duration <= PERFORMANCE_THRESHOLD_MS) {
                    log.debug("✅ ОПТИМИЗАЦИЯ: OrderActivity буферизировано за {}ms", duration);
                } else {
                    log.warn("⚠️ PERFORMANCE: OrderActivity заняло {}ms (цель: <{}ms)",
                            duration, PERFORMANCE_THRESHOLD_MS);
                }

                totalProcessed.incrementAndGet();
            } else {
                log.warn("🚨 BUFFER OVERFLOW: OrderActivity отклонено - буфер переполнен");
            }

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("🚨 ОШИБКА ОПТИМИЗАЦИИ: OrderActivity после {}ms: {}", duration, e.getMessage(), e);
        }
    }

    /**
     * ВЫСОКОПРОИЗВОДИТЕЛЬНОЕ логирование Telegram активности
     */
    @Async("userActivityLoggingExecutor")
    public void logTelegramBotActivityOptimized(Long userId, String username, String firstName, String lastName,
            ActionType actionType, String actionDescription) {

        long startTime = System.currentTimeMillis();

        try {
            UserActivityLogEntity activity = new UserActivityLogEntity(
                    userId, username, firstName, lastName, actionType, actionDescription)
                    .withLogCategory(LogCategory.TELEGRAM_BOT);

            if (addToOptimizedBuffer(activity)) {
                long duration = System.currentTimeMillis() - startTime;

                if (duration <= PERFORMANCE_THRESHOLD_MS) {
                    log.debug("✅ ОПТИМИЗАЦИЯ: TelegramActivity буферизировано за {}ms", duration);
                } else {
                    log.warn("⚠️ PERFORMANCE: TelegramActivity заняло {}ms (цель: <{}ms)",
                            duration, PERFORMANCE_THRESHOLD_MS);
                }

                totalProcessed.incrementAndGet();
            }

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("🚨 ОШИБКА: TelegramActivity после {}ms: {}", duration, e.getMessage(), e);
        }
    }

    /**
     * ВЫСОКОПРОИЗВОДИТЕЛЬНОЕ логирование изменения состояния сессии
     */
    @Async("userActivityLoggingExecutor")
    public void logStateChangeOptimized(UserSessionEntity userSession, String previousState, String newState) {
        try {
            ActionType actionType = determineActionTypeByState(newState);
            String description = String.format("Изменил состояние с %s на %s", previousState, newState);

            UserActivityLogEntity activity = new UserActivityLogEntity(
                    userSession.getUserId(),
                    userSession.getUsername(),
                    userSession.getFirstName(),
                    userSession.getLastName(),
                    actionType,
                    description).withStateChange(previousState, newState);

            addToOptimizedBuffer(activity);
            totalProcessed.incrementAndGet();

        } catch (Exception e) {
            log.error("Ошибка оптимизированного логирования изменения состояния: {}", e.getMessage(), e);
        }
    }

    /**
     * ВЫСОКОПРОИЗВОДИТЕЛЬНОЕ универсальное логирование
     */
    @Async("userActivityLoggingExecutor")
    public void logActivityOptimized(Long userId, String username, String firstName, String lastName,
            ActionType actionType, String actionDescription, LogCategory logCategory) {
        try {
            UserActivityLogEntity activity = new UserActivityLogEntity(
                    userId, username, firstName, lastName, actionType, actionDescription, logCategory);

            addToOptimizedBuffer(activity);
            totalProcessed.incrementAndGet();

        } catch (Exception e) {
            log.error("Ошибка оптимизированного логирования активности: {}", e.getMessage(), e);
        }
    }

    /**
     * КРИТИЧЕСКИ ВАЖНЫЙ МЕТОД: Оптимизированное добавление в буфер
     */
    private boolean addToOptimizedBuffer(UserActivityLogEntity activity) {
        boolean added = activityBuffer.offer(activity);

        if (added) {
            int currentSize = bufferSize.incrementAndGet();

            // Принцип Fail-Fast: автоматическая очистка при достижении оптимального размера
            if (currentSize >= OPTIMAL_BATCH_SIZE) {
                log.debug("🚀 AUTO FLUSH: Достигнут оптимальный размер batch ({})", currentSize);
                flushBufferAsync();
            }
        }

        return added;
    }

    /**
     * КРИТИЧЕСКИ ВАЖНЫЙ МЕТОД: Высокопроизводительная очистка буфера
     */
    @Async("userActivityLoggingExecutor")
    public void flushBufferAsync() {
        flushBufferOptimized();
    }

    /**
     * ОПТИМИЗИРОВАННАЯ очистка буфера с batch операциями
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void flushBufferOptimized() {
        if (activityBuffer.isEmpty()) {
            return;
        }

        long startTime = System.currentTimeMillis();
        int currentBatch = batchCount.incrementAndGet();

        try {
            // Извлекаем оптимальный batch записей
            List<UserActivityLogEntity> batch = new ArrayList<>(OPTIMAL_BATCH_SIZE);
            UserActivityLogEntity activity;

            while ((activity = activityBuffer.poll()) != null && batch.size() < OPTIMAL_BATCH_SIZE) {
                batch.add(activity);
            }

            if (batch.isEmpty()) {
                return;
            }

            // КРИТИЧЕСКАЯ ОПТИМИЗАЦИЯ: Batch сохранение в БД
            long dbStartTime = System.currentTimeMillis();
            List<UserActivityLogEntity> savedActivities = activityLogRepository.saveAll(batch);
            long dbDuration = System.currentTimeMillis() - dbStartTime;

            // Обновляем размер буфера
            bufferSize.addAndGet(-batch.size());

            // Асинхронная обработка SSE
            long sseStartTime = System.currentTimeMillis();
            processSseBatch(savedActivities);
            long sseDuration = System.currentTimeMillis() - sseStartTime;

            long totalDuration = System.currentTimeMillis() - startTime;

            // Логирование производительности
            if (totalDuration <= PERFORMANCE_THRESHOLD_MS) {
                log.info("🚀 BATCH #{}: {} записей за {}ms (БД: {}ms, SSE: {}ms) ✅",
                        currentBatch, batch.size(), totalDuration, dbDuration, sseDuration);
            } else {
                log.warn("⚠️ BATCH #{}: {} записей за {}ms (БД: {}ms, SSE: {}ms) - превышен порог {}ms",
                        currentBatch, batch.size(), totalDuration, dbDuration, sseDuration, PERFORMANCE_THRESHOLD_MS);
            }

            // Статистика производительности
            if (currentBatch % 10 == 0) {
                logPerformanceStats();
            }

        } catch (Exception e) {
            long errorDuration = System.currentTimeMillis() - startTime;
            log.error("🚨 BATCH ERROR #{}: Ошибка после {}ms: {}", currentBatch, errorDuration, e.getMessage(), e);
        }
    }

    /**
     * Асинхронная обработка SSE для batch
     */
    private void processSseBatch(List<UserActivityLogEntity> activities) {
        for (UserActivityLogEntity activity : activities) {
            try {
                sseService.addToRecentActivities(activity);
                sseService.broadcastActivity(activity);
            } catch (Exception e) {
                log.warn("SSE ошибка для активности {}: {}", activity.getId(), e.getMessage());
            }
        }
    }

    /**
     * Запланированная очистка буфера для обеспечения своевременной обработки
     */
    @Scheduled(fixedDelay = FLUSH_INTERVAL_MS)
    public void scheduledFlushBuffer() {
        if (!activityBuffer.isEmpty()) {
            int currentSize = bufferSize.get();
            log.debug("📅 SCHEDULED FLUSH: Очистка буфера (размер: {})", currentSize);
            flushBufferOptimized();
        }
    }

    /**
     * Принудительная очистка всех буферов (для shutdown)
     */
    public void forceFlushAllBuffers() {
        log.info("🚀 FORCE FLUSH: Принудительная очистка всех буферов");
        while (!activityBuffer.isEmpty()) {
            flushBufferOptimized();
        }
        logPerformanceStats();
    }

    /**
     * Статистика производительности
     */
    private void logPerformanceStats() {
        int processed = totalProcessed.get();
        int batches = batchCount.get();
        int avgBatchSize = batches > 0 ? processed / batches : 0;

        log.info("📊 ПРОИЗВОДИТЕЛЬНОСТЬ: Обработано {} записей в {} batch'ах (средний размер: {})",
                processed, batches, avgBatchSize);
        log.info("📊 БУФЕРИЗАЦИЯ: Текущий размер буфера: {}/{}", bufferSize.get(), MAX_BUFFER_SIZE);
    }

    /**
     * Получить метрики производительности
     */
    public PerformanceMetrics getPerformanceMetrics() {
        return new PerformanceMetrics(
                totalProcessed.get(),
                batchCount.get(),
                bufferSize.get(),
                MAX_BUFFER_SIZE,
                OPTIMAL_BATCH_SIZE,
                PERFORMANCE_THRESHOLD_MS);
    }

    /**
     * Определение типа действия по состоянию
     */
    private ActionType determineActionTypeByState(String state) {
        return switch (state) {
            case "IDLE" -> ActionType.SESSION_START;
            case "SELECTING_PACKAGE" -> ActionType.PACKAGE_VIEWED;
            case "CONFIRMING_ORDER" -> ActionType.PACKAGE_SELECTED;
            case "AWAITING_PAYMENT" -> ActionType.ORDER_CREATED;
            case "PAYMENT_PROCESSING" -> ActionType.PAYMENT_INITIATED;
            case "COMPLETED" -> ActionType.ORDER_COMPLETED;
            case "CANCELLED" -> ActionType.ORDER_CANCELLED;
            case "EXPIRED" -> ActionType.SESSION_EXPIRED;
            default -> ActionType.STATE_CHANGED;
        };
    }

    /**
     * Метрики производительности
     */
    public static class PerformanceMetrics {
        public final int totalProcessed;
        public final int batchCount;
        public final int currentBufferSize;
        public final int maxBufferSize;
        public final int optimalBatchSize;
        public final int performanceThresholdMs;

        public PerformanceMetrics(int totalProcessed, int batchCount, int currentBufferSize,
                int maxBufferSize, int optimalBatchSize, int performanceThresholdMs) {
            this.totalProcessed = totalProcessed;
            this.batchCount = batchCount;
            this.currentBufferSize = currentBufferSize;
            this.maxBufferSize = maxBufferSize;
            this.optimalBatchSize = optimalBatchSize;
            this.performanceThresholdMs = performanceThresholdMs;
        }

        @Override
        public String toString() {
            return String.format(
                    "PerformanceMetrics{processed=%d, batches=%d, buffer=%d/%d, batchSize=%d, threshold=%dms}",
                    totalProcessed, batchCount, currentBufferSize, maxBufferSize, optimalBatchSize,
                    performanceThresholdMs);
        }
    }
}