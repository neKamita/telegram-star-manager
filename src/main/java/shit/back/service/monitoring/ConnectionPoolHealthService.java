package shit.back.service.monitoring;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Сервис для health check connection pools
 * РЕФАКТОРИНГ: Выделен из ConnectionPoolMonitoringService для соблюдения SRP
 * 
 * Отвечает только за:
 * - Health check database pool
 * - Health check Redis pool
 * - Базовую проверку состояния
 */
@Slf4j
@Service
public class ConnectionPoolHealthService implements HealthIndicator {

    @Autowired(required = false)
    private DataSource dataSource;

    @Autowired(required = false)
    private RedisConnectionFactory redisConnectionFactory;

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
     * Получить базовую статистику connection pools
     */
    public Map<String, Object> getBasicConnectionPoolStats() {
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
                    boolean isConnected = !connection.isClosed();
                    redisStats.put("connected", isConnected);
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
            long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
            long totalMb = runtime.totalMemory() / 1024 / 1024;
            long maxMb = runtime.maxMemory() / 1024 / 1024;

            memoryStats.put("used_mb", usedMb);
            memoryStats.put("total_mb", totalMb);
            memoryStats.put("max_mb", maxMb);
            stats.put("memory", memoryStats);

        } catch (Exception e) {
            log.error("❌ Ошибка при получении базовой статистики connection pools: {}", e.getMessage());
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    /**
     * Проверить есть ли проблемы с connection pool
     */
    public boolean hasConnectionPoolIssues() {
        try {
            if (dataSource instanceof HikariDataSource) {
                HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
                HikariPoolMXBean poolMXBean = hikariDataSource.getHikariPoolMXBean();

                if (poolMXBean != null) {
                    int activeConnections = poolMXBean.getActiveConnections();
                    int totalConnections = poolMXBean.getTotalConnections();
                    int threadsAwaitingConnection = poolMXBean.getThreadsAwaitingConnection();

                    // Проблемы: все соединения заняты или слишком много ждущих потоков
                    return activeConnections >= totalConnections || threadsAwaitingConnection > 3;
                }
            }
        } catch (Exception e) {
            log.warn("Ошибка проверки проблем connection pool: {}", e.getMessage());
            return true; // В случае ошибки считаем что есть проблемы
        }

        return false;
    }
}