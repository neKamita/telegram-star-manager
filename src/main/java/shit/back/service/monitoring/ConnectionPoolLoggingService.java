package shit.back.service.monitoring;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

/**
 * Сервис для периодического логирования состояния connection pools
 * РЕФАКТОРИНГ: Выделен из ConnectionPoolMonitoringService для соблюдения SRP
 * 
 * Отвечает только за:
 * - Периодическое логирование состояния
 * - Предупреждения о проблемах
 * - Мониторинг использования ресурсов
 */
@Slf4j
@Service
public class ConnectionPoolLoggingService {

    @Autowired(required = false)
    private DataSource dataSource;

    @Autowired(required = false)
    private RedisConnectionFactory redisConnectionFactory;

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
     * Логирование критических проблем с пулом соединений
     */
    public void logCriticalIssues() {
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            HikariPoolMXBean poolMXBean = hikariDataSource.getHikariPoolMXBean();

            if (poolMXBean != null) {
                int activeConnections = poolMXBean.getActiveConnections();
                int totalConnections = poolMXBean.getTotalConnections();
                int threadsAwaitingConnection = poolMXBean.getThreadsAwaitingConnection();

                // Критические условия
                if (activeConnections >= totalConnections && threadsAwaitingConnection > 5) {
                    log.error("🚨 КРИТИЧЕСКАЯ ПРОБЛЕМА: Все соединения заняты, {} потоков ожидают!",
                            threadsAwaitingConnection);
                    log.error("🚨 Возможные причины:");
                    log.error("   - Утечка соединений в коде");
                    log.error("   - Слишком маленький размер пула");
                    log.error("   - Долгие транзакции");
                    log.error("   - Блокировки в базе данных");
                }

                // Предупреждения о высокой нагрузке
                if (activeConnections >= totalConnections * 0.9) {
                    log.warn("⚠️ ВЫСОКАЯ НАГРУЗКА: Использовано {}/{} соединений ({}%)",
                            activeConnections, totalConnections, (activeConnections * 100) / totalConnections);
                }
            }
        }
    }

    /**
     * Логирование детальной информации для диагностики
     */
    public void logDetailedDiagnostics() {
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;

            log.info("🔍 ДЕТАЛЬНАЯ ДИАГНОСТИКА CONNECTION POOL:");
            log.info("  📋 Pool Name: {}", hikariDataSource.getPoolName());
            log.info("  📋 JDBC URL: {}", hikariDataSource.getJdbcUrl());
            log.info("  📋 Driver: {}", hikariDataSource.getDriverClassName());
            log.info("  📋 Max Pool Size: {}", hikariDataSource.getMaximumPoolSize());
            log.info("  📋 Min Idle: {}", hikariDataSource.getMinimumIdle());
            log.info("  📋 Connection Timeout: {}ms", hikariDataSource.getConnectionTimeout());
            log.info("  📋 Idle Timeout: {}ms", hikariDataSource.getIdleTimeout());
            log.info("  📋 Max Lifetime: {}ms", hikariDataSource.getMaxLifetime());
            log.info("  📋 Leak Detection Threshold: {}ms", hikariDataSource.getLeakDetectionThreshold());

            HikariPoolMXBean poolMXBean = hikariDataSource.getHikariPoolMXBean();
            if (poolMXBean != null) {
                log.info("  📊 Текущее состояние:");
                log.info("    - Active: {}", poolMXBean.getActiveConnections());
                log.info("    - Idle: {}", poolMXBean.getIdleConnections());
                log.info("    - Total: {}", poolMXBean.getTotalConnections());
                log.info("    - Waiting: {}", poolMXBean.getThreadsAwaitingConnection());
            }
        }

        // Информация о JVM
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        double memoryUsagePercent = (double) usedMemory / maxMemory * 100;

        log.info("🖥️ JVM MEMORY:");
        log.info("  📊 Used: {}MB / {}MB ({}%)", usedMemory, maxMemory, String.format("%.1f", memoryUsagePercent));
        log.info("  📊 Available Processors: {}", runtime.availableProcessors());
    }

    /**
     * Проверить и логировать состояние пула при запуске
     */
    public void logStartupPoolStatus() {
        log.info("🚀 ПРОВЕРКА CONNECTION POOL ПРИ ЗАПУСКЕ:");

        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            log.info("✅ HikariCP DataSource обнаружен: {}", hikariDataSource.getPoolName());

            if (!hikariDataSource.isClosed()) {
                log.info("✅ Connection pool активен и готов к работе");
                logDatabasePoolStatus();
            } else {
                log.error("❌ Connection pool закрыт!");
            }
        } else {
            log.warn("⚠️ DataSource не является HikariDataSource или отсутствует");
        }

        if (redisConnectionFactory != null) {
            log.info("✅ Redis ConnectionFactory обнаружен");
            logRedisPoolStatus();
        } else {
            log.warn("⚠️ Redis ConnectionFactory отсутствует");
        }
    }
}