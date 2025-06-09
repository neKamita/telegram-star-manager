package shit.back.service;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;

/**
 * Сервис для мониторинга состояния connection pools
 * Отслеживает использование ресурсов и предотвращает утечки соединений
 */
@Slf4j
@Service
public class ConnectionPoolMonitoringService implements HealthIndicator {

    @Autowired(required = false)
    private DataSource dataSource;

    @Autowired(required = false)
    private RedisConnectionFactory redisConnectionFactory;

    private final MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();

    /**
     * Периодический мониторинг connection pools каждые 5 минут
     */
    @Scheduled(fixedRate = 300000) // 5 минут
    public void monitorConnectionPools() {
        try {
            logDatabasePoolStatus();
            logRedisPoolStatus();
        } catch (Exception e) {
            log.warn("⚠️ Ошибка при мониторинге connection pools: {}", e.getMessage());
        }
    }

    /**
     * Логирование состояния database connection pool
     */
    private void logDatabasePoolStatus() {
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            HikariPoolMXBean poolMXBean = hikariDataSource.getHikariPoolMXBean();

            if (poolMXBean != null) {
                int activeConnections = poolMXBean.getActiveConnections();
                int idleConnections = poolMXBean.getIdleConnections();
                int totalConnections = poolMXBean.getTotalConnections();
                int threadsAwaitingConnection = poolMXBean.getThreadsAwaitingConnection();

                log.info("📊 Database Pool Status:");
                log.info("  🔹 Active: {}", activeConnections);
                log.info("  🔹 Idle: {}", idleConnections);
                log.info("  🔹 Total: {}", totalConnections);
                log.info("  🔹 Waiting: {}", threadsAwaitingConnection);

                // Предупреждения при проблемах
                if (activeConnections >= totalConnections * 0.8) {
                    log.warn("⚠️ Высокое использование DB pool: {}/{}", activeConnections, totalConnections);
                }

                if (threadsAwaitingConnection > 0) {
                    log.warn("⚠️ Потоки ожидают DB соединения: {}", threadsAwaitingConnection);
                }
            }
        }
    }

    /**
     * Логирование состояния Redis connection pool
     */
    private void logRedisPoolStatus() {
        if (redisConnectionFactory instanceof LettuceConnectionFactory) {
            LettuceConnectionFactory lettuceFactory = (LettuceConnectionFactory) redisConnectionFactory;
            
            try {
                // Попытка получить информацию о Redis соединении
                var connection = lettuceFactory.getConnection();
                boolean isConnected = !connection.isClosed();
                connection.close();

                log.info("📊 Redis Pool Status:");
                log.info("  🔹 Connected: {}", isConnected);
                log.info("  🔹 Host: {}:{}", lettuceFactory.getHostName(), lettuceFactory.getPort());

            } catch (Exception e) {
                log.warn("⚠️ Проблема с Redis соединением: {}", e.getMessage());
            }
        }
    }

    /**
     * Health check для connection pools
     */
    @Override
    public Health health() {
        Map<String, Object> details = new HashMap<>();
        boolean isHealthy = true;

        try {
            // Проверка Database pool
            if (dataSource instanceof HikariDataSource) {
                HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
                HikariPoolMXBean poolMXBean = hikariDataSource.getHikariPoolMXBean();

                if (poolMXBean != null) {
                    int activeConnections = poolMXBean.getActiveConnections();
                    int totalConnections = poolMXBean.getTotalConnections();
                    int threadsAwaitingConnection = poolMXBean.getThreadsAwaitingConnection();

                    details.put("database.active", activeConnections);
                    details.put("database.total", totalConnections);
                    details.put("database.waiting", threadsAwaitingConnection);

                    // Проверка критических состояний
                    if (activeConnections >= totalConnections || threadsAwaitingConnection > 5) {
                        isHealthy = false;
                        details.put("database.status", "CRITICAL");
                    } else {
                        details.put("database.status", "HEALTHY");
                    }
                }
            }

            // Проверка Redis pool
            if (redisConnectionFactory instanceof LettuceConnectionFactory) {
                try {
                    var connection = redisConnectionFactory.getConnection();
                    boolean redisConnected = !connection.isClosed();
                    connection.close();

                    details.put("redis.connected", redisConnected);
                    details.put("redis.status", redisConnected ? "HEALTHY" : "DISCONNECTED");

                    if (!redisConnected) {
                        isHealthy = false;
                    }
                } catch (Exception e) {
                    details.put("redis.status", "ERROR");
                    details.put("redis.error", e.getMessage());
                    isHealthy = false;
                }
            }

            // JVM Memory info
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            long maxMemory = runtime.maxMemory();

            details.put("memory.used.mb", usedMemory / 1024 / 1024);
            details.put("memory.total.mb", totalMemory / 1024 / 1024);
            details.put("memory.max.mb", maxMemory / 1024 / 1024);
            details.put("memory.usage.percent", (usedMemory * 100) / maxMemory);

            // Проверка критического использования памяти
            if ((usedMemory * 100) / maxMemory > 85) {
                isHealthy = false;
                details.put("memory.status", "CRITICAL");
            } else {
                details.put("memory.status", "HEALTHY");
            }

        } catch (Exception e) {
            log.error("❌ Ошибка при health check connection pools: {}", e.getMessage());
            details.put("error", e.getMessage());
            isHealthy = false;
        }

        return isHealthy ? Health.up().withDetails(details).build() 
                        : Health.down().withDetails(details).build();
    }

    /**
     * Получение текущей статистики connection pools
     */
    public Map<String, Object> getConnectionPoolStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            // Database pool stats
            if (dataSource instanceof HikariDataSource) {
                HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
                HikariPoolMXBean poolMXBean = hikariDataSource.getHikariPoolMXBean();

                if (poolMXBean != null) {
                    Map<String, Object> dbStats = new HashMap<>();
                    dbStats.put("active", poolMXBean.getActiveConnections());
                    dbStats.put("idle", poolMXBean.getIdleConnections());
                    dbStats.put("total", poolMXBean.getTotalConnections());
                    dbStats.put("waiting", poolMXBean.getThreadsAwaitingConnection());
                    stats.put("database", dbStats);
                }
            }

            // Redis pool stats
            if (redisConnectionFactory instanceof LettuceConnectionFactory) {
                LettuceConnectionFactory lettuceFactory = (LettuceConnectionFactory) redisConnectionFactory;
                Map<String, Object> redisStats = new HashMap<>();
                
                try {
                    var connection = lettuceFactory.getConnection();
                    redisStats.put("connected", !connection.isClosed());
                    redisStats.put("host", lettuceFactory.getHostName());
                    redisStats.put("port", lettuceFactory.getPort());
                    connection.close();
                } catch (Exception e) {
                    redisStats.put("connected", false);
                    redisStats.put("error", e.getMessage());
                }
                
                stats.put("redis", redisStats);
            }

            // Memory stats
            Runtime runtime = Runtime.getRuntime();
            Map<String, Object> memoryStats = new HashMap<>();
            memoryStats.put("used_mb", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024);
            memoryStats.put("total_mb", runtime.totalMemory() / 1024 / 1024);
            memoryStats.put("max_mb", runtime.maxMemory() / 1024 / 1024);
            stats.put("memory", memoryStats);

        } catch (Exception e) {
            log.error("❌ Ошибка при получении статистики connection pools: {}", e.getMessage());
            stats.put("error", e.getMessage());
        }

        return stats;
    }
}
