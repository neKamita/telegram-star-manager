package shit.back.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;
import shit.back.model.FeatureFlag;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
public class FeatureFlagRepository {
    
    private static final String FEATURE_FLAG_PREFIX = "feature_flag:";
    private static final String ALL_FLAGS_KEY = "feature_flags:all";
    private static final String STATS_PREFIX = "feature_flag_stats:";
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    private final ObjectMapper objectMapper;
    
    public FeatureFlagRepository() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    public void save(FeatureFlag featureFlag) {
        try {
            String key = FEATURE_FLAG_PREFIX + featureFlag.getName();
            String json = objectMapper.writeValueAsString(featureFlag);
            
            redisTemplate.opsForValue().set(key, json);
            redisTemplate.opsForSet().add(ALL_FLAGS_KEY, featureFlag.getName());
            
            // Устанавливаем TTL если есть enabledUntil
            if (featureFlag.getEnabledUntil() != null) {
                long ttl = java.time.Duration.between(LocalDateTime.now(), featureFlag.getEnabledUntil()).getSeconds();
                if (ttl > 0) {
                    redisTemplate.expire(key, ttl, TimeUnit.SECONDS);
                }
            }
            
            log.info("Feature flag '{}' saved successfully", featureFlag.getName());
            
        } catch (JsonProcessingException e) {
            log.error("Error serializing feature flag '{}': {}", featureFlag.getName(), e.getMessage());
            throw new RuntimeException("Failed to save feature flag", e);
        }
    }
    
    public Optional<FeatureFlag> findByName(String name) {
        try {
            String key = FEATURE_FLAG_PREFIX + name;
            String json = redisTemplate.opsForValue().get(key);
            
            if (json == null) {
                return Optional.empty();
            }
            
            FeatureFlag featureFlag = objectMapper.readValue(json, FeatureFlag.class);
            return Optional.of(featureFlag);
            
        } catch (JsonProcessingException e) {
            log.error("Error deserializing feature flag '{}': {}", name, e.getMessage());
            return Optional.empty();
        }
    }
    
    public List<FeatureFlag> findAll() {
        Set<String> flagNames = redisTemplate.opsForSet().members(ALL_FLAGS_KEY);
        if (flagNames == null || flagNames.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<FeatureFlag> flags = new ArrayList<>();
        for (String name : flagNames) {
            findByName(name).ifPresent(flags::add);
        }
        
        return flags;
    }
    
    public void delete(String name) {
        String key = FEATURE_FLAG_PREFIX + name;
        redisTemplate.delete(key);
        redisTemplate.opsForSet().remove(ALL_FLAGS_KEY, name);
        
        // Удаляем статистику
        String statsKey = STATS_PREFIX + name;
        redisTemplate.delete(statsKey);
        
        log.info("Feature flag '{}' deleted successfully", name);
    }
    
    public boolean exists(String name) {
        String key = FEATURE_FLAG_PREFIX + name;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
    
    public void updateUsageStats(String flagName) {
        try {
            // Обновляем статистику использования
            String statsKey = STATS_PREFIX + flagName;
            redisTemplate.opsForHash().increment(statsKey, "usage_count", 1);
            redisTemplate.opsForHash().put(statsKey, "last_used", LocalDateTime.now().toString());
            
            // Обновляем статистику в самом флаге
            Optional<FeatureFlag> flagOpt = findByName(flagName);
            if (flagOpt.isPresent()) {
                FeatureFlag flag = flagOpt.get();
                flag.updateUsageStats();
                save(flag);
            }
            
        } catch (Exception e) {
            log.error("Error updating usage stats for flag '{}': {}", flagName, e.getMessage());
        }
    }
    
    public Map<String, Object> getUsageStats(String flagName) {
        String statsKey = STATS_PREFIX + flagName;
        Map<Object, Object> stats = redisTemplate.opsForHash().entries(statsKey);
        
        Map<String, Object> result = new HashMap<>();
        stats.forEach((k, v) -> result.put(k.toString(), v));
        
        return result;
    }
    
    public List<FeatureFlag> findActiveFlags() {
        return findAll().stream()
                .filter(FeatureFlag::isActive)
                .toList();
    }
    
    public List<FeatureFlag> findFlagsForUser(String userId) {
        return findAll().stream()
                .filter(flag -> flag.isEnabledForUser(userId))
                .toList();
    }
    
    public void enableFlag(String name, String updatedBy) {
        Optional<FeatureFlag> flagOpt = findByName(name);
        if (flagOpt.isPresent()) {
            FeatureFlag flag = flagOpt.get();
            flag.setEnabled(true);
            flag.setUpdatedAt(LocalDateTime.now());
            flag.setUpdatedBy(updatedBy);
            save(flag);
            log.info("Feature flag '{}' enabled by {}", name, updatedBy);
        }
    }
    
    public void disableFlag(String name, String updatedBy) {
        Optional<FeatureFlag> flagOpt = findByName(name);
        if (flagOpt.isPresent()) {
            FeatureFlag flag = flagOpt.get();
            flag.setEnabled(false);
            flag.setUpdatedAt(LocalDateTime.now());
            flag.setUpdatedBy(updatedBy);
            save(flag);
            log.info("Feature flag '{}' disabled by {}", name, updatedBy);
        }
    }
    
    public void toggleFlag(String name, String updatedBy) {
        Optional<FeatureFlag> flagOpt = findByName(name);
        if (flagOpt.isPresent()) {
            FeatureFlag flag = flagOpt.get();
            flag.setEnabled(!flag.isEnabled());
            flag.setUpdatedAt(LocalDateTime.now());
            flag.setUpdatedBy(updatedBy);
            save(flag);
            log.info("Feature flag '{}' toggled to {} by {}", name, flag.isEnabled(), updatedBy);
        }
    }
}
