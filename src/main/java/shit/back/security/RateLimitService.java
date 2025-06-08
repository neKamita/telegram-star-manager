package shit.back.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import shit.back.config.SecurityConstants;
import shit.back.config.SecurityProperties;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RateLimitService {
    
    @Autowired
    private SecurityProperties securityProperties;
    
    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;
    
    // Fallback для случая когда Redis недоступен
    private final ConcurrentHashMap<String, RateLimitInfo> inMemoryLimits = new ConcurrentHashMap<>();
    
    /**
     * Проверка лимита для пользователя Telegram
     */
    public RateLimitResult checkUserLimit(Long userId) {
        if (!securityProperties.getRateLimit().isEnabled()) {
            return RateLimitResult.allowed();
        }
        
        String key = SecurityConstants.RATE_LIMIT_USER_PREFIX + userId;
        return checkLimit(key, securityProperties.getRateLimit().getUserRequestsPerMinute(), "User " + userId);
    }
    
    /**
     * Проверка лимита для API запросов
     */
    public RateLimitResult checkApiLimit(String clientIp) {
        if (!securityProperties.getRateLimit().isEnabled()) {
            return RateLimitResult.allowed();
        }
        
        String key = SecurityConstants.RATE_LIMIT_API_PREFIX + clientIp;
        return checkLimit(key, securityProperties.getRateLimit().getApiRequestsPerMinute(), "API client " + clientIp);
    }
    
    /**
     * Проверка лимита с использованием Redis или in-memory fallback
     */
    private RateLimitResult checkLimit(String key, int maxRequests, String clientInfo) {
        try {
            if (redisTemplate != null) {
                return checkLimitWithRedis(key, maxRequests, clientInfo);
            } else {
                log.debug("Redis not available, using in-memory rate limiting");
                return checkLimitInMemory(key, maxRequests, clientInfo);
            }
        } catch (Exception e) {
            log.warn("Error checking rate limit, allowing request: {}", e.getMessage());
            return RateLimitResult.allowed();
        }
    }
    
    /**
     * Проверка лимита с использованием Redis
     */
    private RateLimitResult checkLimitWithRedis(String key, int maxRequests, String clientInfo) {
        try {
            // Получаем текущее количество запросов
            String currentValue = redisTemplate.opsForValue().get(key);
            int currentRequests = currentValue != null ? Integer.parseInt(currentValue) : 0;
            
            if (currentRequests >= maxRequests) {
                log.warn("Rate limit exceeded for {}: {} requests in last minute", 
                    clientInfo, currentRequests);
                
                // Получаем TTL для информации о времени сброса
                Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                return RateLimitResult.rateLimited(maxRequests, currentRequests, ttl != null ? ttl.intValue() : 60);
            }
            
            // Увеличиваем счетчик
            redisTemplate.opsForValue().increment(key);
            
            // Устанавливаем TTL если это первый запрос
            if (currentRequests == 0) {
                redisTemplate.expire(key, Duration.ofSeconds(SecurityConstants.RATE_LIMIT_WINDOW));
            }
            
            int remainingRequests = maxRequests - currentRequests - 1;
            log.debug("Rate limit check passed for {}: {} remaining requests", 
                clientInfo, remainingRequests);
            
            return RateLimitResult.allowed(remainingRequests);
            
        } catch (Exception e) {
            log.debug("Redis unavailable, switching to in-memory rate limiting: {}", e.getMessage());
            return checkLimitInMemory(key, maxRequests, clientInfo);
        }
    }
    
    /**
     * In-memory fallback для rate limiting
     */
    private RateLimitResult checkLimitInMemory(String key, int maxRequests, String clientInfo) {
        long currentTime = System.currentTimeMillis();
        
        RateLimitInfo info = inMemoryLimits.compute(key, (k, existing) -> {
            if (existing == null) {
                return new RateLimitInfo(1, currentTime);
            }
            
            // Проверяем, прошла ли минута
            if (currentTime - existing.windowStart > SecurityConstants.RATE_LIMIT_WINDOW * 1000) {
                return new RateLimitInfo(1, currentTime);
            }
            
            return new RateLimitInfo(existing.requestCount + 1, existing.windowStart);
        });
        
        if (info.requestCount > maxRequests) {
            log.warn("Rate limit exceeded for {} (in-memory): {} requests in last minute", 
                clientInfo, info.requestCount);
            
            long remainingTime = SecurityConstants.RATE_LIMIT_WINDOW - 
                (currentTime - info.windowStart) / 1000;
            
            return RateLimitResult.rateLimited(maxRequests, info.requestCount, (int) remainingTime);
        }
        
        int remainingRequests = maxRequests - info.requestCount;
        log.debug("Rate limit check passed for {} (in-memory): {} remaining requests", 
            clientInfo, remainingRequests);
        
        return RateLimitResult.allowed(remainingRequests);
    }
    
    /**
     * Очистка старых записей из in-memory кеша
     */
    public void cleanupInMemoryLimits() {
        long currentTime = System.currentTimeMillis();
        long cutoffTime = currentTime - SecurityConstants.RATE_LIMIT_WINDOW * 1000;
        
        inMemoryLimits.entrySet().removeIf(entry -> 
            entry.getValue().windowStart < cutoffTime);
        
        log.debug("Cleaned up {} old rate limit entries", inMemoryLimits.size());
    }
    
    /**
     * Информация о rate limiting для in-memory хранения
     */
    private static class RateLimitInfo {
        final int requestCount;
        final long windowStart;
        
        RateLimitInfo(int requestCount, long windowStart) {
            this.requestCount = requestCount;
            this.windowStart = windowStart;
        }
    }
    
    /**
     * Результат проверки rate limit
     */
    public static class RateLimitResult {
        private final boolean allowed;
        private final int maxRequests;
        private final int currentRequests;
        private final int remainingRequests;
        private final int resetTimeSeconds;
        
        private RateLimitResult(boolean allowed, int maxRequests, int currentRequests, 
                              int remainingRequests, int resetTimeSeconds) {
            this.allowed = allowed;
            this.maxRequests = maxRequests;
            this.currentRequests = currentRequests;
            this.remainingRequests = remainingRequests;
            this.resetTimeSeconds = resetTimeSeconds;
        }
        
        public static RateLimitResult allowed() {
            return new RateLimitResult(true, 0, 0, 0, 0);
        }
        
        public static RateLimitResult allowed(int remainingRequests) {
            return new RateLimitResult(true, 0, 0, remainingRequests, 0);
        }
        
        public static RateLimitResult rateLimited(int maxRequests, int currentRequests, int resetTimeSeconds) {
            return new RateLimitResult(false, maxRequests, currentRequests, 0, resetTimeSeconds);
        }
        
        public boolean isAllowed() {
            return allowed;
        }
        
        public int getMaxRequests() {
            return maxRequests;
        }
        
        public int getCurrentRequests() {
            return currentRequests;
        }
        
        public int getRemainingRequests() {
            return remainingRequests;
        }
        
        public int getResetTimeSeconds() {
            return resetTimeSeconds;
        }
        
        public String getErrorMessage() {
            if (allowed) {
                return null;
            }
            return String.format("Rate limit exceeded. %d/%d requests used. Reset in %d seconds.", 
                currentRequests, maxRequests, resetTimeSeconds);
        }
    }
}
