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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import jakarta.annotation.PostConstruct;
import java.time.Duration;

/**
 * Конфигурация кэширования с fallback логикой
 * Автоматически переключается между Redis и локальным кэшем
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

    @Autowired(required = false)
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private ObjectMapper objectMapper;

    @PostConstruct
    public void logCacheConfiguration() {
        log.info("🔧 КОНФИГУРАЦИЯ КЭША: Тип кэша из конфигурации: {}", cacheType);
        log.info("🔧 КОНФИГУРАЦИЯ КЭША: Redis ConnectionFactory доступен: {}",
                redisConnectionFactory != null);
    }

    /**
     * Основной CacheManager с fallback логикой
     */
    @Bean
    @Primary
    public CacheManager cacheManager() {
        log.info("🔧 НАСТРОЙКА КЭША: Создание CacheManager...");

        // Пытаемся создать Redis CacheManager
        if (isRedisAvailable()) {
            try {
                RedisCacheManager redisCacheManager = createRedisCacheManager();
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
    private RedisCacheManager createRedisCacheManager() {
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
        return RedisCacheManager.builder(redisConnectionFactory)
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
    public RedisTemplate<String, Object> redisTemplate() {
        if (!isRedisAvailable()) {
            log.warn("⚠️ REDIS: RedisTemplate не создан - Redis недоступен");
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
     * Проверка доступности Redis
     */
    private boolean isRedisAvailable() {
        if (redisConnectionFactory == null) {
            log.debug("🔍 REDIS: ConnectionFactory отсутствует");
            return false;
        }

        try {
            // Проверяем соединение
            redisConnectionFactory.getConnection().ping();
            log.debug("✅ REDIS: Соединение успешно проверено");
            return true;
        } catch (Exception e) {
            log.warn("⚠️ REDIS: Проверка соединения неудачна: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Диагностика кэша при старте приложения
     */
    @Bean
    public CacheDiagnosticService cacheDiagnosticService(CacheManager cacheManager) {
        return new CacheDiagnosticService(cacheManager, redisConnectionFactory);
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