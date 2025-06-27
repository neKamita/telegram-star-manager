package shit.back.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.concurrent.Executor;

/**
 * Configuration for admin dashboard performance optimization
 * Optimized for Koyeb's limited resources (0.1 vCPU, 512MB RAM)
 *
 * ИСПРАВЛЕНО: Циклическая зависимость устранена с помощью @Lazy аннотации
 */
@Configuration
@EnableCaching
@EnableAsync
@EnableScheduling
public class AdminPerformanceConfig {

    private static final Logger log = LoggerFactory.getLogger(AdminPerformanceConfig.class);

    @Lazy
    @Autowired(required = false)
    private CacheMetricsInterceptor cacheMetricsInterceptor;

    /**
     * Cache manager for admin dashboard data
     * Uses simple concurrent map cache for minimal memory usage
     * ОБНОВЛЕНО: интеграция с CacheMetricsInterceptor для отслеживания статистики
     */
    @Bean("adminCacheManager")
    public CacheManager adminCacheManager() {
        log.info("Configuring admin cache manager for performance optimization");

        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(
                "admin_performance",
                "admin_recent_activity",
                "systemHealth");
        cacheManager.setAllowNullValues(false);

        // Интеграция с метриками кэша (если доступен)
        if (cacheMetricsInterceptor != null) {
            log.info("✅ CACHE METRICS: CacheMetricsInterceptor интегрирован с admin cache manager");
            // Примечание: интеграция происходит через @Cacheable аннотации и Spring AOP
        } else {
            log.warn("⚠️ CACHE METRICS: CacheMetricsInterceptor не доступен");
        }

        log.info("Admin cache manager configured with {} cache regions",
                cacheManager.getCacheNames().size());

        return cacheManager;
    }

    /**
     * Thread pool executor for async admin operations
     * Optimized for Koyeb's 0.1 vCPU limitation
     */
    @Bean("adminAsyncExecutor")
    public Executor adminAsyncExecutor() {
        log.info("Configuring admin async executor for limited resources");

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Minimal thread pool для экономии ресурсов
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("AdminAsync-");

        // Настройки для быстрой очистки неактивных потоков
        executor.setKeepAliveSeconds(30);
        executor.setAllowCoreThreadTimeOut(true);

        // Политика отклонения - выполнять в текущем потоке
        executor.setRejectedExecutionHandler((runnable, executor1) -> {
            log.warn("Admin async task queue full, executing in current thread");
            runnable.run();
        });

        executor.initialize();

        log.info("Admin async executor configured: core={}, max={}, queue={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }

    /**
     * Scheduled task executor for cache maintenance
     */
    @Bean("adminScheduledExecutor")
    public Executor adminScheduledExecutor() {
        log.info("Configuring admin scheduled executor");

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Один поток для scheduled задач
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(5);
        executor.setThreadNamePrefix("AdminScheduled-");
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);

        executor.initialize();

        log.info("Admin scheduled executor configured");
        return executor;
    }

    /**
     * Dedicated thread pool executor для Background Metrics Service
     * Отдельный от основного async executor для изоляции
     */
    @Bean("metricsBackgroundExecutor")
    public Executor metricsBackgroundExecutor() {
        log.info("Configuring dedicated metrics background executor");

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Один dedicated thread для метрик
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(3); // Маленькая очередь для метрик
        executor.setThreadNamePrefix("MetricsBG-");

        // Быстрая очистка для экономии ресурсов
        executor.setKeepAliveSeconds(30);
        executor.setAllowCoreThreadTimeOut(true);

        // Политика отклонения - логирование и пропуск
        executor.setRejectedExecutionHandler((runnable, executor1) -> {
            log.warn("Background metrics task rejected - queue full, skipping collection cycle");
            // Пропускаем этот цикл сбора метрик вместо блокировки
        });

        executor.initialize();

        log.info("Metrics background executor configured: dedicated thread for performance monitoring");
        return executor;
    }

    /**
     * Task Scheduler для отложенных задач (включая тестовые платежи)
     */
    @Bean("taskScheduler")
    public TaskScheduler taskScheduler() {
        log.info("Configuring task scheduler for delayed operations");

        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

        // Минимальный пул для scheduled задач
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("TaskScheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);

        scheduler.initialize();

        log.info("Task scheduler configured with pool size: {}", scheduler.getPoolSize());
        return scheduler;
    }
}
