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
     * Отдельный executor для операций логирования активности пользователей
     * 
     * ПАРАМЕТРЫ ОПТИМИЗАЦИИ:
     * - core-size: 5 (базовое количество потоков)
     * - max-size: 15 (максимальное количество потоков)
     * - queue-capacity: 100 (размер очереди задач)
     * - keep-alive: 60s (время жизни неактивных потоков)
     */
    @Bean(name = "userActivityLoggingExecutor")
    public Executor userActivityLoggingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Основные параметры пула
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(15);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);

        // Настройки именования и обработки
        executor.setThreadNamePrefix("logging-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        // Стратегия отклонения задач при переполнении
        executor.setRejectedExecutionHandler((runnable, executor1) -> {
            log.warn("🚨 ASYNC LOGGING: Задача отклонена из-за переполнения пула. Выполняется синхронно.");
            if (!executor1.isShutdown()) {
                runnable.run();
            }
        });

        executor.initialize();

        log.info("🚀 ASYNC CONFIG: Инициализирован UserActivityLoggingExecutor (core={}, max={}, queue={})",
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