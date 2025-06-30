package shit.back.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shit.back.entity.UserSessionEntity;
import shit.back.model.UserSession;
import shit.back.model.UserCountsBatchResult;
import shit.back.model.Order;
import shit.back.model.StarPackage;
import shit.back.repository.UserSessionJpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ВЫСОКОПРОИЗВОДИТЕЛЬНЫЙ сервис управления пользовательскими сессиями
 * 
 * КРИТИЧЕСКИЕ ОПТИМИЗАЦИИ:
 * 1. Кэширование часто используемых запросов
 * 2. Batch операции для снижения нагрузки на БД
 * 3. Асинхронная обработка неблокирующих операций
 * 4. Оптимизированные SQL запросы с составными индексами
 * 5. In-memory кэш для активных сессий
 * 
 * ЦЕЛЕВЫЕ ПОКАЗАТЕЛИ:
 * - createOrUpdateSessionEntity: с 100+ms до <25ms
 * - getUserCountsBatch: оптимизация N+1 проблемы
 * - Cache hit ratio: >90%
 * 
 * Принципы: SOLID, DRY, Clean Code, KISS, Fail-Fast, YAGNI
 */
@Slf4j
@Service
@Transactional
public class OptimizedUserSessionService {

    @Autowired
    private UserSessionJpaRepository sessionRepository;

    // Высокопроизводительный in-memory кэш
    private final Map<Long, UserSession> activeSessionsCache = new ConcurrentHashMap<>();
    private final Map<String, Order> ordersCache = new ConcurrentHashMap<>();

    // Метрики производительности
    private final AtomicInteger cacheHits = new AtomicInteger(0);
    private final AtomicInteger cacheMisses = new AtomicInteger(0);
    private final AtomicInteger dbOperations = new AtomicInteger(0);

    // Пороги производительности
    private static final int PERFORMANCE_THRESHOLD_MS = 25;
    private static final int CACHE_SIZE_LIMIT = 10000;
    private static final int CLEANUP_BATCH_SIZE = 100;

    /**
     * ОПТИМИЗИРОВАННОЕ получение или создание сессии
     * Цель: <25ms вместо 100+ms
     */
    public UserSession getOrCreateSessionOptimized(Long userId, String username, String firstName, String lastName) {
        long startTime = System.currentTimeMillis();

        try {
            // Валидация (принцип Fail-Fast)
            if (userId == null || userId <= 0) {
                throw new IllegalArgumentException("Invalid user ID: " + userId);
            }

            // Проверяем in-memory кэш сначала
            UserSession cachedSession = activeSessionsCache.get(userId);
            if (cachedSession != null) {
                cachedSession.updateActivity();
                cacheHits.incrementAndGet();

                long duration = System.currentTimeMillis() - startTime;
                log.debug("✅ CACHE HIT: Сессия для userId={} получена за {}ms", userId, duration);

                // Асинхронное обновление в БД
                updateSessionAsyncIfNeeded(cachedSession);
                return cachedSession;
            }

            cacheMisses.incrementAndGet();

            // Создаем новую сессию
            UserSession session = new UserSession(userId, username, firstName, lastName);

            // Добавляем в кэш
            if (activeSessionsCache.size() < CACHE_SIZE_LIMIT) {
                activeSessionsCache.put(userId, session);
            } else {
                log.warn("⚠️ CACHE FULL: Достигнут лимит кэша {}, очистка старых записей", CACHE_SIZE_LIMIT);
                cleanupOldCacheEntries();
            }

            // Асинхронная синхронизация с PostgreSQL
            synchronizeSessionAsyncOptimized(session);

            long duration = System.currentTimeMillis() - startTime;
            if (duration <= PERFORMANCE_THRESHOLD_MS) {
                log.debug("✅ ОПТИМИЗАЦИЯ: Новая сессия создана за {}ms", duration);
            } else {
                log.warn("⚠️ PERFORMANCE: Создание сессии заняло {}ms (цель: <{}ms)",
                        duration, PERFORMANCE_THRESHOLD_MS);
            }

            return session;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("🚨 ОШИБКА: getOrCreateSession для userId={} после {}ms: {}",
                    userId, duration, e.getMessage(), e);
            throw new RuntimeException("Failed to get or create session", e);
        }
    }

    /**
     * ОПТИМИЗИРОВАННОЕ обновление состояния сессии
     */
    public void updateSessionStateOptimized(Long userId, UserSession.SessionState state) {
        long startTime = System.currentTimeMillis();

        try {
            if (userId == null || userId <= 0 || state == null) {
                throw new IllegalArgumentException("Invalid parameters: userId=" + userId + ", state=" + state);
            }

            UserSession session = activeSessionsCache.get(userId);
            if (session != null) {
                session.setState(state);
                session.updateActivity();

                // Асинхронное обновление в БД
                updateSessionAsyncIfNeeded(session);

                long duration = System.currentTimeMillis() - startTime;
                log.debug("✅ STATE UPDATE: userId={}, state={} за {}ms", userId, state, duration);
            } else {
                log.warn("⚠️ SESSION NOT FOUND: userId={} не найден в кэше для обновления состояния", userId);
            }

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("🚨 ОШИБКА: updateSessionState для userId={} после {}ms: {}",
                    userId, duration, e.getMessage(), e);
        }
    }

    /**
     * ВЫСОКОПРОИЗВОДИТЕЛЬНАЯ синхронизация с БД
     */
    @Async("userActivityLoggingExecutor")
    public void synchronizeSessionAsyncOptimized(UserSession userSession) {
        long startTime = System.currentTimeMillis();

        try {
            createOrUpdateSessionEntityOptimized(userSession);

            long duration = System.currentTimeMillis() - startTime;
            if (duration <= PERFORMANCE_THRESHOLD_MS) {
                log.debug("✅ ASYNC SYNC: Сессия синхронизирована за {}ms", duration);
            } else {
                log.warn("⚠️ SLOW SYNC: Синхронизация заняла {}ms (цель: <{}ms)",
                        duration, PERFORMANCE_THRESHOLD_MS);
            }

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("🚨 SYNC ERROR: Ошибка синхронизации после {}ms: {}", duration, e.getMessage(), e);
        }
    }

    /**
     * КРИТИЧЕСКИ ОПТИМИЗИРОВАННОЕ создание/обновление entity в PostgreSQL
     * Цель: <25ms вместо 100+ms
     */
    @Transactional
    public UserSessionEntity createOrUpdateSessionEntityOptimized(UserSession userSession) {
        long startTime = System.currentTimeMillis();
        dbOperations.incrementAndGet();

        try {
            Optional<UserSessionEntity> existingOpt = sessionRepository.findByUserId(userSession.getUserId());

            UserSessionEntity entity;
            if (existingOpt.isPresent()) {
                // Обновляем существующую entity
                entity = existingOpt.get();
                updateEntityFromSession(entity, userSession);
            } else {
                // Создаем новую entity
                entity = createEntityFromSession(userSession);
            }

            UserSessionEntity saved = sessionRepository.save(entity);

            long duration = System.currentTimeMillis() - startTime;
            if (duration <= PERFORMANCE_THRESHOLD_MS) {
                log.debug("✅ DB OPTIMIZED: Сессия сохранена за {}ms", duration);
            } else {
                log.warn("🚨 DB SLOW: Сохранение заняло {}ms (цель: <{}ms) для userId={}",
                        duration, PERFORMANCE_THRESHOLD_MS, userSession.getUserId());
            }

            return saved;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("🚨 DB ERROR: Ошибка сохранения сессии userId={} после {}ms: {}",
                    userSession.getUserId(), duration, e.getMessage(), e);
            throw new RuntimeException("Failed to create/update session entity", e);
        }
    }

    /**
     * ОПТИМИЗИРОВАННЫЙ batch запрос для решения N+1 проблемы
     */
    @Cacheable(value = "userCountsCache", key = "'batch_counts'")
    @Transactional(readOnly = true)
    public UserCountsBatchResult getUserCountsBatchOptimized() {
        long startTime = System.currentTimeMillis();

        try {
            LocalDateTime activeThreshold = LocalDateTime.now().minusHours(24);
            LocalDateTime onlineThreshold = LocalDateTime.now().minusMinutes(5);

            UserCountsBatchResult result = sessionRepository.getUserCountsBatch(activeThreshold, onlineThreshold);

            long duration = System.currentTimeMillis() - startTime;
            log.info("🚀 BATCH OPTIMIZED: getUserCountsBatch за {}ms - SINGLE SQL вместо 3 запросов!", duration);
            log.debug("📊 РЕЗУЛЬТАТ: Total={}, Active={}, Online={}",
                    result.totalUsers(), result.activeUsers(), result.onlineUsers());

            return result;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("🚨 BATCH ERROR: Ошибка batch запроса после {}ms: {}", duration, e.getMessage(), e);
            return new UserCountsBatchResult(0L, 0L, 0L);
        }
    }

    /**
     * ОПТИМИЗИРОВАННОЕ получение активных сессий с кэшированием
     */
    @Cacheable(value = "activeSessionsCache", key = "'active_sessions'")
    @Transactional(readOnly = true)
    public List<UserSessionEntity> getActiveSessionsOptimized() {
        long startTime = System.currentTimeMillis();

        try {
            List<UserSessionEntity> sessions = sessionRepository.findByIsActiveTrueOrderByLastActivityDesc();

            long duration = System.currentTimeMillis() - startTime;
            log.debug("✅ ACTIVE SESSIONS: Получено {} сессий за {}ms", sessions.size(), duration);

            return sessions;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("🚨 ACTIVE SESSIONS ERROR: Ошибка после {}ms: {}", duration, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Асинхронное обновление сессии если требуется
     */
    @Async("userActivityLoggingExecutor")
    private void updateSessionAsyncIfNeeded(UserSession session) {
        if (shouldUpdateSession(session)) {
            synchronizeSessionAsyncOptimized(session);
        }
    }

    /**
     * Проверка необходимости обновления сессии в БД
     */
    private boolean shouldUpdateSession(UserSession session) {
        // Обновляем если последняя активность была более 1 минуты назад
        return session.getLastActivity().isBefore(LocalDateTime.now().minusMinutes(1));
    }

    /**
     * Очистка старых записей из кэша
     */
    private void cleanupOldCacheEntries() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(2);
        int removedCount = 0;

        activeSessionsCache.entrySet().removeIf(entry -> {
            if (entry.getValue().getLastActivity().isBefore(cutoff)) {
                return true;
            }
            return false;
        });

        if (removedCount > 0) {
            log.info("🧹 CACHE CLEANUP: Удалено {} старых записей из кэша", removedCount);
        }
    }

    /**
     * Запланированная очистка кэша
     */
    @Scheduled(fixedRate = 300000) // каждые 5 минут
    @CacheEvict(value = { "userCountsCache", "activeSessionsCache" }, allEntries = true)
    public void scheduledCacheCleanup() {
        cleanupOldCacheEntries();

        // Логируем статистику кэша
        logCacheStatistics();
    }

    /**
     * Статистика производительности кэша
     */
    private void logCacheStatistics() {
        int hits = cacheHits.get();
        int misses = cacheMisses.get();
        int total = hits + misses;
        double hitRatio = total > 0 ? (double) hits / total * 100 : 0;

        log.info("📊 CACHE STATS: Попаданий: {}, Промахов: {}, Hit Ratio: {:.1f}%, DB операций: {}",
                hits, misses, hitRatio, dbOperations.get());
        log.info("📊 CACHE SIZE: Активных сессий в кэше: {}/{}",
                activeSessionsCache.size(), CACHE_SIZE_LIMIT);
    }

    /**
     * Получить метрики производительности
     */
    public PerformanceMetrics getPerformanceMetrics() {
        int hits = cacheHits.get();
        int misses = cacheMisses.get();
        int total = hits + misses;
        double hitRatio = total > 0 ? (double) hits / total * 100 : 0;

        return new PerformanceMetrics(
                hits, misses, hitRatio,
                activeSessionsCache.size(), CACHE_SIZE_LIMIT,
                dbOperations.get(), PERFORMANCE_THRESHOLD_MS);
    }

    /**
     * Вспомогательные методы для работы с entity
     */
    private void updateEntityFromSession(UserSessionEntity entity, UserSession session) {
        entity.setUsername(session.getUsername());
        entity.setFirstName(session.getFirstName());
        entity.setLastName(session.getLastName());

        if (session.getState() != null) {
            entity.setState(convertSessionState(session.getState()));
        }

        if (session.getOrderId() != null) {
            entity.setCurrentOrderId(session.getOrderId());
        }

        if (session.getPaymentType() != null) {
            entity.setPaymentType(session.getPaymentType());
        }

        entity.updateActivity();
    }

    private UserSessionEntity createEntityFromSession(UserSession session) {
        UserSessionEntity entity = new UserSessionEntity(
                session.getUserId(),
                session.getUsername(),
                session.getFirstName(),
                session.getLastName());

        if (session.getState() != null) {
            entity.setState(convertSessionState(session.getState()));
        }

        if (session.getOrderId() != null) {
            entity.setCurrentOrderId(session.getOrderId());
        }

        if (session.getPaymentType() != null) {
            entity.setPaymentType(session.getPaymentType());
        }

        return entity;
    }

    /**
     * Конвертация состояния сессии
     */
    private UserSessionEntity.SessionState convertSessionState(UserSession.SessionState state) {
        return switch (state) {
            case IDLE -> UserSessionEntity.SessionState.IDLE;
            case SELECTING_PACKAGE -> UserSessionEntity.SessionState.SELECTING_PACKAGE;
            case CONFIRMING_ORDER -> UserSessionEntity.SessionState.CONFIRMING_ORDER;
            case AWAITING_PAYMENT -> UserSessionEntity.SessionState.AWAITING_PAYMENT;
            case PAYMENT_PROCESSING -> UserSessionEntity.SessionState.PAYMENT_PROCESSING;
            case COMPLETED -> UserSessionEntity.SessionState.COMPLETED;
            case TOPPING_UP_BALANCE -> UserSessionEntity.SessionState.TOPPING_UP_BALANCE;
            case SELECTING_PAYMENT_TYPE -> UserSessionEntity.SessionState.SELECTING_PAYMENT_TYPE;
            case BALANCE_PAYMENT_PROCESSING -> UserSessionEntity.SessionState.BALANCE_PAYMENT_PROCESSING;
            case MIXED_PAYMENT_PROCESSING -> UserSessionEntity.SessionState.MIXED_PAYMENT_PROCESSING;
            case ENTERING_CUSTOM_AMOUNT -> UserSessionEntity.SessionState.ENTERING_CUSTOM_AMOUNT;
        };
    }

    /**
     * Метрики производительности
     */
    public static class PerformanceMetrics {
        public final int cacheHits;
        public final int cacheMisses;
        public final double hitRatio;
        public final int cacheSize;
        public final int maxCacheSize;
        public final int dbOperations;
        public final int performanceThresholdMs;

        public PerformanceMetrics(int cacheHits, int cacheMisses, double hitRatio,
                int cacheSize, int maxCacheSize, int dbOperations, int performanceThresholdMs) {
            this.cacheHits = cacheHits;
            this.cacheMisses = cacheMisses;
            this.hitRatio = hitRatio;
            this.cacheSize = cacheSize;
            this.maxCacheSize = maxCacheSize;
            this.dbOperations = dbOperations;
            this.performanceThresholdMs = performanceThresholdMs;
        }

        @Override
        public String toString() {
            return String.format(
                    "SessionMetrics{hits=%d, misses=%d, hitRatio=%.1f%%, cache=%d/%d, dbOps=%d, threshold=%dms}",
                    cacheHits, cacheMisses, hitRatio, cacheSize, maxCacheSize, dbOperations, performanceThresholdMs);
        }
    }
}