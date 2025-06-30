package shit.back.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * НОВЫЙ СЕРВИС: Диагностика Redis соединения с детальным анализом проблем
 * 
 * Основные функции:
 * - Проверка состояния LettuceConnectionFactory при запуске
 * - Периодический мониторинг Redis соединения
 * - Автоматическое восстановление соединения при сбоях
 * - Детальная диагностика причин недоступности Redis
 * 
 * Принципы SOLID:
 * - Single Responsibility: только диагностика Redis
 * - Open/Closed: легко расширить новыми проверками
 * - Dependency Inversion: работает с абстракциями
 */
@Slf4j
@Component
public class RedisConnectionDiagnosticService {

    private final RedisConnectionFactory redisConnectionFactory;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    private volatile boolean redisHealthy = false;
    private volatile int consecutiveFailures = 0;
    private static final int MAX_CONSECUTIVE_FAILURES = 5;

    public RedisConnectionDiagnosticService(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    /**
     * Диагностика при старте приложения
     */
    @EventListener(ApplicationReadyEvent.class)
    public void performStartupRedisDiagnostic() {
        log.info("🚀 REDIS STARTUP DIAGNOSTIC: ===== НАЧАЛО ДИАГНОСТИКИ =====");

        if (redisConnectionFactory == null) {
            log.error("❌ КРИТИЧЕСКАЯ ОШИБКА: RedisConnectionFactory равен NULL!");
            log.error("❌ Возможные причины:");
            log.error("   1. Ошибка в создании бина RedisConnectionFactory");
            log.error("   2. Исключение при конфигурации Redis");
            log.error("   3. Конфликт между автоконфигурацией и явной конфигурацией");
            return;
        }

        // Проверяем тип ConnectionFactory
        diagnoseFacotryType();

        // Проверяем состояние LettuceConnectionFactory
        diagnoseLettuceConnectionState();

        // Тестируем подключение
        testRedisConnection();

        // Проверяем настройки
        diagnoseRedisSettings();

        log.info("🚀 REDIS STARTUP DIAGNOSTIC: ===== ДИАГНОСТИКА ЗАВЕРШЕНА =====");
        log.info("🚀 СТАТУС: Redis {} (Последовательные ошибки: {})",
                redisHealthy ? "РАБОТАЕТ" : "НЕ ДОСТУПЕН", consecutiveFailures);
    }

    /**
     * Диагностика типа ConnectionFactory
     */
    private void diagnoseFacotryType() {
        log.info("🔍 ТИП FACTORY: {}", redisConnectionFactory.getClass().getSimpleName());

        if (redisConnectionFactory instanceof LettuceConnectionFactory) {
            LettuceConnectionFactory lettuce = (LettuceConnectionFactory) redisConnectionFactory;
            log.info("✅ LettuceConnectionFactory обнаружен");
            log.info("🔍 Host: {}", lettuce.getHostName());
            log.info("🔍 Port: {}", lettuce.getPort());
            log.info("🔍 Database: {}", lettuce.getDatabase());

            // Проверяем статус соединения
            checkLettuceConnectionStatus(lettuce);
        } else {
            log.warn("⚠️ Неожиданный тип ConnectionFactory: {}",
                    redisConnectionFactory.getClass().getName());
        }
    }

    /**
     * Проверка статуса LettuceConnectionFactory
     */
    private void checkLettuceConnectionStatus(LettuceConnectionFactory lettuce) {
        try {
            // Попытка получить соединение для проверки статуса
            var connection = lettuce.getConnection();

            if (connection == null) {
                log.error("❌ КРИТИЧЕСКАЯ ОШИБКА: getConnection() вернул NULL!");
                log.error("❌ Это указывает на то, что LettuceConnectionFactory в состоянии STOPPED");

                // Попытка вызвать start()
                try {
                    log.info("🔄 ПОПЫТКА ВОССТАНОВЛЕНИЯ: Вызываем start()...");
                    lettuce.start();
                    log.info("✅ ВОССТАНОВЛЕНИЕ: start() выполнен успешно");

                    // Повторная проверка
                    var newConnection = lettuce.getConnection();
                    if (newConnection != null) {
                        log.info("✅ ВОССТАНОВЛЕНИЕ: Соединение восстановлено");
                        newConnection.close();
                    } else {
                        log.error("❌ ВОССТАНОВЛЕНИЕ: start() не помог, соединение все еще NULL");
                    }
                } catch (Exception startEx) {
                    log.error("❌ ВОССТАНОВЛЕНИЕ: Ошибка вызова start(): {}", startEx.getMessage());
                }
            } else {
                log.info("✅ СОЕДИНЕНИЕ: getConnection() успешно вернул соединение");
                connection.close();
            }

        } catch (Exception e) {
            log.error("❌ ОШИБКА ПРОВЕРКИ: Не удалось получить соединение: {}", e.getMessage());
            log.error("❌ Тип исключения: {}", e.getClass().getSimpleName());

            if (e.getMessage() != null && e.getMessage().contains("STOPPED")) {
                log.error("❌ ПОДТВЕРЖДЕНИЕ: LettuceConnectionFactory находится в состоянии STOPPED!");
            }
        }
    }

    /**
     * Диагностика состояния LettuceConnectionFactory
     */
    private void diagnoseLettuceConnectionState() {
        if (!(redisConnectionFactory instanceof LettuceConnectionFactory)) {
            return;
        }

        LettuceConnectionFactory lettuce = (LettuceConnectionFactory) redisConnectionFactory;

        log.info("🔍 АНАЛИЗ СОСТОЯНИЯ LettuceConnectionFactory:");

        try {
            // Пытаемся получить детальную информацию
            log.info("🔍 Host: {}", lettuce.getHostName());
            log.info("🔍 Port: {}", lettuce.getPort());
            log.info("🔍 Database: {}", lettuce.getDatabase());
            log.info("🔍 Timeout: {}", lettuce.getTimeout());

            // Проверяем есть ли пароль
            boolean hasPassword = lettuce.getPassword() != null && !lettuce.getPassword().isEmpty();
            log.info("🔍 Password configured: {}", hasPassword);

        } catch (Exception e) {
            log.warn("⚠️ Не удалось получить детали LettuceConnectionFactory: {}", e.getMessage());
        }
    }

    /**
     * Тестирование Redis подключения
     */
    private void testRedisConnection() {
        log.info("🔍 ТЕСТИРОВАНИЕ ПОДКЛЮЧЕНИЯ К REDIS:");

        try {
            var connection = redisConnectionFactory.getConnection();

            if (connection == null) {
                log.error("❌ ТЕСТ: getConnection() вернул NULL - Redis недоступен");
                redisHealthy = false;
                consecutiveFailures++;
                return;
            }

            // Тест ping
            try {
                connection.ping();
                log.info("✅ ТЕСТ: PING успешен");
                redisHealthy = true;
                consecutiveFailures = 0;
            } catch (Exception pingEx) {
                log.error("❌ ТЕСТ: PING неудачен: {}", pingEx.getMessage());
                redisHealthy = false;
                consecutiveFailures++;
            }

            // Тест записи/чтения
            try {
                String testKey = "diagnostic_test_" + System.currentTimeMillis();
                String testValue = "test_value";

                connection.set(testKey.getBytes(), testValue.getBytes());
                byte[] result = connection.get(testKey.getBytes());

                if (result != null && testValue.equals(new String(result))) {
                    log.info("✅ ТЕСТ: SET/GET операции успешны");
                    connection.del(testKey.getBytes()); // Очищаем
                } else {
                    log.warn("⚠️ ТЕСТ: SET/GET операции вернули неверный результат");
                }

            } catch (Exception setGetEx) {
                log.warn("⚠️ ТЕСТ: Ошибка SET/GET операций: {}", setGetEx.getMessage());
            }

            connection.close();

        } catch (Exception e) {
            log.error("❌ ТЕСТ: Критическая ошибка тестирования: {}", e.getMessage());
            log.error("❌ ТЕСТ: Stack trace: ", e);
            redisHealthy = false;
            consecutiveFailures++;
        }
    }

    /**
     * Диагностика настроек Redis
     */
    private void diagnoseRedisSettings() {
        log.info("🔍 АНАЛИЗ НАСТРОЕК REDIS:");
        log.info("🔍 Configured Host: {}", redisHost);
        log.info("🔍 Configured Port: {}", redisPort);
        log.info("🔍 Configured Database: {}", redisDatabase);

        // Сравниваем с LettuceConnectionFactory настройками
        if (redisConnectionFactory instanceof LettuceConnectionFactory) {
            LettuceConnectionFactory lettuce = (LettuceConnectionFactory) redisConnectionFactory;

            boolean hostMatches = redisHost.equals(lettuce.getHostName());
            boolean portMatches = redisPort == lettuce.getPort();
            boolean dbMatches = redisDatabase == lettuce.getDatabase();

            log.info("🔍 СОПОСТАВЛЕНИЕ НАСТРОЕК:");
            log.info("🔍 Host matches: {} (config: {}, factory: {})", hostMatches, redisHost, lettuce.getHostName());
            log.info("🔍 Port matches: {} (config: {}, factory: {})", portMatches, redisPort, lettuce.getPort());
            log.info("🔍 DB matches: {} (config: {}, factory: {})", dbMatches, redisDatabase, lettuce.getDatabase());

            if (!hostMatches || !portMatches || !dbMatches) {
                log.warn("⚠️ ОБНАРУЖЕНО НЕСООТВЕТСТВИЕ в настройках Redis!");
            }
        }
    }

    /**
     * Периодическая проверка здоровья Redis (каждые 2 минуты)
     */
    @Scheduled(fixedRate = 120000)
    public void periodicRedisHealthCheck() {
        log.debug("🏥 PERIODIC REDIS CHECK: Проверка здоровья Redis...");

        if (redisConnectionFactory == null) {
            log.warn("⚠️ PERIODIC CHECK: RedisConnectionFactory недоступен");
            return;
        }

        try {
            var connection = redisConnectionFactory.getConnection();
            if (connection != null) {
                connection.ping();
                connection.close();

                if (!redisHealthy) {
                    log.info("✅ RECOVERY: Redis восстановлен после сбоя");
                    redisHealthy = true;
                    consecutiveFailures = 0;
                }

                log.debug("✅ PERIODIC CHECK: Redis работает корректно");
            } else {
                log.warn("⚠️ PERIODIC CHECK: getConnection() вернул NULL");
                handleRedisFailure("getConnection returned NULL");
            }

        } catch (Exception e) {
            log.warn("⚠️ PERIODIC CHECK: Redis недоступен: {}", e.getMessage());
            handleRedisFailure(e.getMessage());
        }
    }

    /**
     * Обработка сбоя Redis
     */
    private void handleRedisFailure(String reason) {
        redisHealthy = false;
        consecutiveFailures++;

        log.warn("⚠️ REDIS FAILURE: {} (последовательные ошибки: {})", reason, consecutiveFailures);

        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            log.error("🚨 КРИТИЧЕСКИЙ СБОЙ REDIS: {} последовательных ошибок!", consecutiveFailures);
            log.error("🚨 Рекомендации:");
            log.error("   1. Проверить доступность Redis сервера");
            log.error("   2. Проверить настройки подключения");
            log.error("   3. Перезапустить приложение или Redis сервер");
        }
    }

    /**
     * Публичные методы для других сервисов
     */
    public boolean isRedisHealthy() {
        return redisHealthy;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public void forceRedisHealthCheck() {
        log.info("🔧 FORCE HEALTH CHECK: Принудительная проверка Redis");
        testRedisConnection();
    }
}