package shit.back.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Конфигурация асинхронного выполнения для оптимизации производительности
 * 
 * КРИТИЧЕСКАЯ ОПТИМИЗАЦИЯ:
 * - Создает отдельный thread pool для операций логирования
 * - Оптимизированные размеры пула для снижения нагрузки на БД
 * - Асинхронное выполнение UserActivityLoggingService операций
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfiguration {

    /**
     * КРИТИЧЕСКИ ОПТИМИЗИРОВАННЫЙ executor для операций логирования активности
     *
     * НОВЫЕ ПАРАМЕТРЫ ОПТИМИЗАЦИИ для снижения с 180-257ms до <30ms:
     * - core-size: 8 (увеличен для лучшего параллелизма)
     * - max-size: 25 (увеличен для пиковых нагрузок)
     * - queue-capacity: 200 (увеличен буфер для batch операций)
     * - keep-alive: 120s (дольше держим потоки активными)
     */
    @Bean(name = "userActivityLoggingExecutor")
    public Executor userActivityLoggingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // КРИТИЧЕСКИ ОПТИМИЗИРОВАННЫЕ параметры для высокой производительности
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(25);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(120);

        // Настройки для максимальной производительности
        executor.setThreadNamePrefix("hp-logging-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(180);
        executor.setAllowCoreThreadTimeOut(false); // Держим core потоки активными

        // ОПТИМИЗИРОВАННАЯ стратегия отклонения с метриками
        executor.setRejectedExecutionHandler((runnable, executor1) -> {
            log.error("🚨 CRITICAL: Пул логирования переполнен! Активных потоков: {}, Очередь: {}",
                    executor1.getActiveCount(), executor1.getQueue().size());

            // Выполняем синхронно как fallback
            if (!executor1.isShutdown()) {
                long startTime = System.currentTimeMillis();
                runnable.run();
                long duration = System.currentTimeMillis() - startTime;
                log.warn("⚠️ FALLBACK: Задача выполнена синхронно за {}ms", duration);
            }
        });

        executor.initialize();

        log.info("🚀 HIGH PERFORMANCE: UserActivityLoggingExecutor оптимизирован (core={}, max={}, queue={})",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        log.info("🎯 ЦЕЛЬ: Снижение DB операций с 180-257ms до <30ms");

        return executor;
    }

    /**
     * НОВЫЙ: Специализированный executor для DB операций
     */
    @Bean(name = "databaseOperationExecutor")
    public Executor databaseOperationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Оптимизированные параметры для DB операций
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(12);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(90);

        executor.setThreadNamePrefix("db-ops-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);

        // Специальная обработка для DB операций
        executor.setRejectedExecutionHandler((runnable, executor1) -> {
            log.error("🚨 DB POOL OVERFLOW: DB пул переполнен! Выполняем синхронно.");
            if (!executor1.isShutdown()) {
                runnable.run();
            }
        });

        executor.initialize();

        log.info("🚀 DB OPTIMIZED: DatabaseOperationExecutor создан (core={}, max={}, queue={})",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }

    /**
     * НОВЫЙ: Executor для кэш операций
     */
    @Bean(name = "cacheOperationExecutor")
    public Executor cacheOperationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Быстрые операции для кэша
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);

        executor.setThreadNamePrefix("cache-ops-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.setRejectedExecutionHandler((runnable, executor1) -> {
            log.warn("⚠️ CACHE EXECUTOR: Пул кэша переполнен, выполняем синхронно");
            if (!executor1.isShutdown()) {
                runnable.run();
            }
        });

        executor.initialize();

        log.info("🚀 CACHE OPTIMIZED: CacheOperationExecutor создан (core={}, max={}, queue={})",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }

    /**
     * Основной асинхронный executor для общих задач
     * 
     * Более консервативные настройки для общих асинхронных операций
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(60);

        executor.setThreadNamePrefix("async-task-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();

        log.info("🚀 ASYNC CONFIG: Инициализирован общий TaskExecutor (core={}, max={}, queue={})",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }
}