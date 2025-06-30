package shit.back.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import jakarta.annotation.PostConstruct;
import java.time.Duration;

/**
 * ИСПРАВЛЕННАЯ конфигурация кэширования с fallback логикой
 * Автоматически переключается между Redis и локальным кэшем
 * 
 * ОСНОВНЫЕ ИСПРАВЛЕНИЯ:
 * 1. Явное создание RedisConnectionFactory с правильной инициализацией
 * 2. Вызов start() для LettuceConnectionFactory
 * 3. Детальное логирование для диагностики
 * 4. Fallback механизм при недоступности Redis
 * 
 * Принципы SOLID:
 * - Single Responsibility: только конфигурация кэширования
 * - Open/Closed: легко расширить новыми провайдерами
 * - Dependency Inversion: зависимость от абстракций
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig {

    @Value("${spring.cache.type:simple}")
    private String cacheType;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    @Autowired
    private ObjectMapper objectMapper;

    private volatile RedisConnectionFactory createdRedisConnectionFactory;

    @PostConstruct
    public void logCacheConfiguration() {
        log.info("🔧 КОНФИГУРАЦИЯ КЭША: Тип кэша из конфигурации: {}", cacheType);
        log.info("🔧 КОНФИГУРАЦИЯ КЭША: Создаем собственный Redis ConnectionFactory");
        log.info("🔧 КОНФИГУРАЦИЯ КЭША: Redis настройки - Host: {}:{}, DB: {}", redisHost, redisPort, redisDatabase);
    }

    /**
     * ИСПРАВЛЕНИЕ: Создание собственного RedisConnectionFactory с правильной
     * инициализацией
     */
    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        log.info("🔧 ИСПРАВЛЕНИЕ: Создание собственного RedisConnectionFactory...");

        try {
            // Создаем LettuceConnectionFactory с явными настройками
            LettuceConnectionFactory factory = new LettuceConnectionFactory(redisHost, redisPort);

            // Настраиваем password если указан
            if (redisPassword != null && !redisPassword.trim().isEmpty()) {
                factory.setPassword(redisPassword);
                log.info("🔧 ИСПРАВЛЕНИЕ: Redis password настроен");
            }

            // Настраиваем database
            factory.setDatabase(redisDatabase);

            // КРИТИЧЕСКИ ВАЖНО: Инициализируем factory
            factory.afterPropertiesSet();

            // КРИТИЧЕСКИ ВАЖНО: Запускаем factory
            factory.start();

            log.info("✅ ИСПРАВЛЕНИЕ: LettuceConnectionFactory создан и запущен");
            log.info("✅ ИСПРАВЛЕНИЕ: Host: {}:{}, Database: {}", redisHost, redisPort, redisDatabase);

            // Проверяем что factory работает
            try {
                var connection = factory.getConnection();
                connection.ping();
                connection.close();
                log.info("✅ ИСПРАВЛЕНИЕ: Проверка ping успешна");

                // Сохраняем ссылку для других методов
                this.createdRedisConnectionFactory = factory;

            } catch (Exception pingEx) {
                log.error("❌ ИСПРАВЛЕНИЕ: Ping неудачен: {}", pingEx.getMessage());
                throw pingEx;
            }

            return factory;

        } catch (Exception e) {
            log.error("❌ ИСПРАВЛЕНИЕ: Не удалось создать RedisConnectionFactory: {}", e.getMessage());
            log.warn("🔄 ИСПРАВЛЕНИЕ: Система будет использовать локальный кэш");
            this.createdRedisConnectionFactory = null;
            return null;
        }
    }

    /**
     * Основной CacheManager с fallback логикой
     */
    @Bean
    @Primary
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        log.info("🔧 НАСТРОЙКА КЭША: Создание CacheManager...");

        // Пытаемся создать Redis CacheManager
        if (redisConnectionFactory != null && isRedisAvailable(redisConnectionFactory)) {
            try {
                RedisCacheManager redisCacheManager = createRedisCacheManager(redisConnectionFactory);
                log.info("✅ КЭШ: Успешно создан Redis CacheManager");
                return redisCacheManager;
            } catch (Exception e) {
                log.warn("⚠️ КЭШ: Ошибка создания Redis CacheManager: {}", e.getMessage());
            }
        }

        // Fallback к локальному кэшу
        log.warn("🔄 КЭШ: Переключение на локальный кэш (fallback)");
        return createLocalCacheManager();
    }

    /**
     * Создание Redis CacheManager с правильным ObjectMapper
     * ИСПРАВЛЕНИЕ: Использует наш ObjectMapper с JSR310 поддержкой
     */
    private RedisCacheManager createRedisCacheManager(RedisConnectionFactory factory) {
        log.info("🔧 REDIS: Создание Redis CacheManager с типизированной сериализацией...");

        // Создаем типизированный сериализатор для SystemHealth
        Jackson2JsonRedisSerializer<shit.back.dto.monitoring.SystemHealth> systemHealthSerializer = new Jackson2JsonRedisSerializer<>(
                shit.back.dto.monitoring.SystemHealth.class);
        systemHealthSerializer.setObjectMapper(objectMapper);

        // Общий JSON сериализатор для остальных кэшей
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        // Конфигурация для systemHealth кэша с типизированным сериализатором
        RedisCacheConfiguration systemHealthConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                                .fromSerializer(systemHealthSerializer))
                .disableCachingNullValues();

        // Конфигурация по умолчанию для остальных кэшей
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .serializeKeysWith(org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                                .fromSerializer(jsonSerializer))
                .disableCachingNullValues();

        log.info("✅ ИСПРАВЛЕНИЕ: Redis CacheManager с типизированной сериализацией для SystemHealth");
        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("systemHealth", systemHealthConfig)
                .build();
    }

    /**
     * Создание локального CacheManager как fallback
     */
    private CacheManager createLocalCacheManager() {
        log.info("🔧 LOCAL: Создание локального CacheManager...");

        // Используем Caffeine если доступен, иначе ConcurrentMap
        try {
            CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
            caffeineCacheManager.setCacheNames(
                    java.util.Arrays.asList(
                            "admin_performance",
                            "admin_recent_activity",
                            "admin_dashboard_cache",
                            "systemHealth"));
            caffeineCacheManager.setCacheSpecification("maximumSize=100,expireAfterWrite=5m");
            log.info("✅ LOCAL: Создан Caffeine CacheManager");
            return caffeineCacheManager;
        } catch (Exception e) {
            log.warn("⚠️ LOCAL: Caffeine недоступен, используем ConcurrentMap: {}", e.getMessage());
            ConcurrentMapCacheManager concurrentMapCacheManager = new ConcurrentMapCacheManager(
                    "admin_performance",
                    "admin_recent_activity",
                    "admin_dashboard_cache",
                    "systemHealth");
            log.info("✅ LOCAL: Создан ConcurrentMap CacheManager");
            return concurrentMapCacheManager;
        }
    }

    /**
     * RedisTemplate для прямой работы с Redis
     * ИСПРАВЛЕНИЕ: Использует наш ObjectMapper с JSR310 поддержкой
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        if (redisConnectionFactory == null) {
            log.warn("⚠️ REDIS: RedisTemplate не создан - RedisConnectionFactory недоступен");
            return null;
        }

        try {
            // ИСПРАВЛЕНИЕ: Используем наш ObjectMapper с поддержкой LocalDateTime
            GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

            RedisTemplate<String, Object> template = new RedisTemplate<>();
            template.setConnectionFactory(redisConnectionFactory);
            template.setKeySerializer(new StringRedisSerializer());
            template.setValueSerializer(jsonSerializer);
            template.setHashKeySerializer(new StringRedisSerializer());
            template.setHashValueSerializer(jsonSerializer);
            template.afterPropertiesSet();

            log.info("✅ ИСПРАВЛЕНИЕ: RedisTemplate создан с ObjectMapper поддержкой JSR310");
            return template;
        } catch (Exception e) {
            log.error("❌ REDIS: Ошибка создания RedisTemplate: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Проверка доступности Redis с детальным логированием
     */
    private boolean isRedisAvailable(RedisConnectionFactory factory) {
        if (factory == null) {
            log.warn("🔍 REDIS ДИАГНОСТИКА: ConnectionFactory отсутствует");
            return false;
        }

        log.info("🔍 REDIS ДИАГНОСТИКА: ConnectionFactory найден: {}",
                factory.getClass().getSimpleName());

        // Проверяем является ли это LettuceConnectionFactory
        if (factory instanceof LettuceConnectionFactory) {
            LettuceConnectionFactory lettuceFactory = (LettuceConnectionFactory) factory;

            log.info("🔍 REDIS ДИАГНОСТИКА: LettuceConnectionFactory обнаружен");
            log.info("🔍 REDIS ДИАГНОСТИКА: Host: {}:{}", lettuceFactory.getHostName(), lettuceFactory.getPort());

            // КРИТИЧЕСКИ ВАЖНО: Проверяем статус фабрики
            try {
                boolean isStarted = lettuceFactory.getConnection() != null;
                log.info("🔍 REDIS ДИАГНОСТИКА: Connection доступен: {}", isStarted);
            } catch (Exception e) {
                log.error("❌ REDIS ДИАГНОСТИКА: LettuceConnectionFactory has been STOPPED! Ошибка: {}", e.getMessage());
                log.error("❌ REDIS ДИАГНОСТИКА: Попытка вызвать start() для инициализации...");

                try {
                    lettuceFactory.start();
                    log.info("✅ REDIS ДИАГНОСТИКА: start() вызван успешно");
                } catch (Exception startEx) {
                    log.error("❌ REDIS ДИАГНОСТИКА: Не удалось вызвать start(): {}", startEx.getMessage());
                    return false;
                }
            }
        }

        try {
            // Проверяем соединение
            var connection = factory.getConnection();
            connection.ping();
            connection.close();
            log.info("✅ REDIS ДИАГНОСТИКА: Ping успешен - Redis доступен");
            return true;
        } catch (Exception e) {
            log.error("❌ REDIS ДИАГНОСТИКА: Ping неудачен: {}", e.getMessage());
            log.error("❌ REDIS ДИАГНОСТИКА: Возможные причины:");
            log.error("   1. Redis сервер недоступен");
            log.error("   2. Неправильные credentials");
            log.error("   3. LettuceConnectionFactory не инициализирован (STOPPED)");
            log.error("   4. Проблемы с сетью/firewall");
            return false;
        }
    }

    /**
     * Диагностика кэша при старте приложения
     * ИСПРАВЛЕНО: Явно указан основной CacheManager с @Qualifier
     */
    @Bean
    public CacheDiagnosticService cacheDiagnosticService(@Qualifier("cacheManager") CacheManager cacheManager) {
        return new CacheDiagnosticService(cacheManager, this.createdRedisConnectionFactory);
    }

    /**
     * Сервис диагностики кэша
     */
    public static class CacheDiagnosticService {
        private final CacheManager cacheManager;
        private final RedisConnectionFactory redisConnectionFactory;

        public CacheDiagnosticService(CacheManager cacheManager, RedisConnectionFactory redisConnectionFactory) {
            this.cacheManager = cacheManager;
            this.redisConnectionFactory = redisConnectionFactory;
            performStartupDiagnostic();
        }

        private void performStartupDiagnostic() {
            log.info("🔍 ДИАГНОСТИКА КЭША: ===== ЗАПУСК ДИАГНОСТИКИ =====");

            // Проверка CacheManager
            log.info("🔍 ДИАГНОСТИКА: CacheManager тип: {}",
                    cacheManager != null ? cacheManager.getClass().getSimpleName() : "NULL");

            if (cacheManager != null) {
                log.info("🔍 ДИАГНОСТИКА: Доступные кэши: {}", cacheManager.getCacheNames());
            }

            // Проверка Redis
            if (redisConnectionFactory != null) {
                try {
                    redisConnectionFactory.getConnection().ping();
                    log.info("✅ ДИАГНОСТИКА: Redis ДОСТУПЕН");
                } catch (Exception e) {
                    log.error("❌ ДИАГНОСТИКА: Redis НЕ ДОСТУПЕН: {}", e.getMessage());
                }
            } else {
                log.error("❌ ДИАГНОСТИКА: Redis ConnectionFactory НЕ НАСТРОЕН");
            }

            log.info("🔍 ДИАГНОСТИКА КЭША: ===== КОНЕЦ ДИАГНОСТИКИ =====");
        }

        public boolean isRedisAvailable() {
            if (redisConnectionFactory == null) {
                return false;
            }
            try {
                redisConnectionFactory.getConnection().ping();
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        public String getCacheProviderType() {
            if (cacheManager == null) {
                return "NONE";
            }
            return cacheManager.getClass().getSimpleName();
        }
    }
}