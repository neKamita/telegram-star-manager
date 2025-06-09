package shit.back.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for admin dashboard performance optimization
 * Optimized for Koyeb's limited resources (0.1 vCPU, 512MB RAM)
 */
@Slf4j
@Configuration
@EnableCaching
@EnableAsync
@EnableScheduling
public class AdminPerformanceConfig {

    /**
     * Cache manager for admin dashboard data
     * Uses simple concurrent map cache for minimal memory usage
     */
    @Bean("adminCacheManager")
    public CacheManager adminCacheManager() {
        log.info("Configuring admin cache manager for performance optimization");
        
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(
            "admin_performance",
            "admin_recent_activity", 
            "admin_dashboard_cache",
            "admin_user_counts",
            "admin_lightweight"
        );
        cacheManager.setAllowNullValues(false);
        
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
}
