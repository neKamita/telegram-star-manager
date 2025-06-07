package shit.back.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import shit.back.entity.FeatureFlagEntity;
import shit.back.model.FeatureFlag;
import shit.back.repository.FeatureFlagJpaRepository;
import shit.back.repository.FeatureFlagRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FeatureFlagService {
    
    @Autowired(required = false)
    private FeatureFlagRepository redisRepository;
    
    @Autowired
    private FeatureFlagJpaRepository jpaRepository;
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    // Кэш для быстрого доступа к флагам
    private final Map<String, FeatureFlag> flagCache = new ConcurrentHashMap<>();
    
    public boolean isFeatureEnabled(String flagName) {
        return isFeatureEnabled(flagName, null);
    }
    
    public boolean isFeatureEnabled(String flagName, String userId) {
        try {
            FeatureFlag flag = getFlagFromCache(flagName);
            if (flag == null) {
                log.debug("Feature flag '{}' not found, defaulting to false", flagName);
                return false;
            }
            
            boolean enabled;
            if (userId != null) {
                enabled = flag.isEnabledForUser(userId);
            } else {
                enabled = flag.isActive();
            }
            
            if (enabled) {
                // Обновляем статистику использования (с fallback)
                try {
                    updateUsageStats(flagName);
                } catch (Exception e) {
                    log.warn("Failed to update usage stats for flag '{}': {}", flagName, e.getMessage());
                    // Продолжаем работу даже если статистика не обновилась
                }
            }
            
            log.debug("Feature flag '{}' check for user '{}': {}", flagName, userId, enabled);
            return enabled;
            
        } catch (Exception e) {
            log.error("Error checking feature flag '{}': {}", flagName, e.getMessage());
            return false; // Fail-safe: если ошибка - функция отключена
        }
    }
    
    private FeatureFlag getFlagFromCache(String flagName) {
        FeatureFlag cachedFlag = flagCache.get(flagName);
        if (cachedFlag != null) {
            return cachedFlag;
        }
        
        // Загружаем из JPA repository
        Optional<FeatureFlagEntity> entityOpt = jpaRepository.findById(flagName);
        if (entityOpt.isPresent()) {
            FeatureFlag flag = entityOpt.get().toModel();
            flagCache.put(flagName, flag);
            return flag;
        }
        
        return null;
    }
    
    public FeatureFlag createFeatureFlag(FeatureFlag featureFlag) {
        // Устанавливаем значения по умолчанию
        if (featureFlag.getCreatedAt() == null) {
            featureFlag.setCreatedAt(LocalDateTime.now());
        }
        featureFlag.setUpdatedAt(LocalDateTime.now());
        
        if (featureFlag.getUsageCount() == null) {
            featureFlag.setUsageCount(0L);
        }
        
        // Сохраняем в JPA
        FeatureFlagEntity entity = FeatureFlagEntity.fromModel(featureFlag);
        jpaRepository.save(entity);
        
        // Обновляем кэш
        flagCache.put(featureFlag.getName(), featureFlag);
        
        // Пробуем синхронизировать с Redis
        syncToRedis(featureFlag);
        
        // Публикуем событие
        publishFeatureFlagEvent("CREATED", featureFlag);
        
        log.info("Feature flag '{}' created successfully", featureFlag.getName());
        return featureFlag;
    }
    
    public FeatureFlag updateFeatureFlag(String flagName, FeatureFlag updates) {
        Optional<FeatureFlagEntity> existingOpt = jpaRepository.findById(flagName);
        if (existingOpt.isEmpty()) {
            throw new RuntimeException("Feature flag '" + flagName + "' not found");
        }
        
        FeatureFlag existing = existingOpt.get().toModel();
        
        // Обновляем поля
        if (updates.getDescription() != null) {
            existing.setDescription(updates.getDescription());
        }
        if (updates.isEnabled() != existing.isEnabled()) {
            existing.setEnabled(updates.isEnabled());
        }
        if (updates.getRolloutPercentage() != null) {
            existing.setRolloutPercentage(updates.getRolloutPercentage());
        }
        if (updates.getEnabledForUsers() != null) {
            existing.setEnabledForUsers(updates.getEnabledForUsers());
        }
        if (updates.getEnabledFrom() != null) {
            existing.setEnabledFrom(updates.getEnabledFrom());
        }
        if (updates.getEnabledUntil() != null) {
            existing.setEnabledUntil(updates.getEnabledUntil());
        }
        if (updates.getUpdatedBy() != null) {
            existing.setUpdatedBy(updates.getUpdatedBy());
        }
        
        existing.setUpdatedAt(LocalDateTime.now());
        
        // Сохраняем в JPA
        FeatureFlagEntity entity = FeatureFlagEntity.fromModel(existing);
        jpaRepository.save(entity);
        
        // Обновляем кэш
        flagCache.put(flagName, existing);
        
        // Пробуем синхронизировать с Redis
        syncToRedis(existing);
        
        // Публикуем событие
        publishFeatureFlagEvent("UPDATED", existing);
        
        log.info("Feature flag '{}' updated successfully", flagName);
        return existing;
    }
    
    public void deleteFeatureFlag(String flagName) {
        Optional<FeatureFlagEntity> flagOpt = jpaRepository.findById(flagName);
        if (flagOpt.isEmpty()) {
            throw new RuntimeException("Feature flag '" + flagName + "' not found");
        }
        
        FeatureFlag flag = flagOpt.get().toModel();
        
        // Удаляем из JPA
        jpaRepository.deleteById(flagName);
        
        // Удаляем из кэша
        flagCache.remove(flagName);
        
        // Пробуем удалить из Redis
        deleteFromRedis(flagName);
        
        // Публикуем событие
        publishFeatureFlagEvent("DELETED", flag);
        
        log.info("Feature flag '{}' deleted successfully", flagName);
    }
    
    public List<FeatureFlag> getAllFeatureFlags() {
        try {
            return jpaRepository.findAll().stream()
                    .map(FeatureFlagEntity::toModel)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("JPA repository unavailable, returning cached flags: {}", e.getMessage());
            return new java.util.ArrayList<>(flagCache.values());
        }
    }
    
    public Optional<FeatureFlag> getFeatureFlag(String flagName) {
        try {
            return jpaRepository.findById(flagName)
                    .map(FeatureFlagEntity::toModel);
        } catch (Exception e) {
            log.warn("JPA repository unavailable, checking cache for flag '{}': {}", flagName, e.getMessage());
            return Optional.ofNullable(flagCache.get(flagName));
        }
    }
    
    public List<FeatureFlag> getActiveFeatureFlags() {
        try {
            return jpaRepository.findActiveFlags(LocalDateTime.now()).stream()
                    .map(FeatureFlagEntity::toModel)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("JPA repository unavailable, filtering cached flags: {}", e.getMessage());
            return flagCache.values().stream()
                .filter(FeatureFlag::isEnabled)
                .collect(Collectors.toList());
        }
    }
    
    public List<FeatureFlag> getUserFeatureFlags(String userId) {
        // Для простоты возвращаем все активные флаги
        return getActiveFeatureFlags().stream()
                .filter(flag -> flag.isEnabledForUser(userId))
                .collect(Collectors.toList());
    }
    
    public void enableFeatureFlag(String flagName, String updatedBy) {
        toggleFeatureFlagInternal(flagName, true, updatedBy);
        publishFeatureFlagEvent("ENABLED", getFeatureFlag(flagName).orElse(null));
    }
    
    public void disableFeatureFlag(String flagName, String updatedBy) {
        toggleFeatureFlagInternal(flagName, false, updatedBy);
        publishFeatureFlagEvent("DISABLED", getFeatureFlag(flagName).orElse(null));
    }
    
    public void toggleFeatureFlag(String flagName, String updatedBy) {
        Optional<FeatureFlagEntity> entityOpt = jpaRepository.findById(flagName);
        if (entityOpt.isPresent()) {
            FeatureFlagEntity entity = entityOpt.get();
            toggleFeatureFlagInternal(flagName, !entity.isEnabled(), updatedBy);
            publishFeatureFlagEvent("TOGGLED", getFeatureFlag(flagName).orElse(null));
        }
    }
    
    private void toggleFeatureFlagInternal(String flagName, boolean enabled, String updatedBy) {
        Optional<FeatureFlagEntity> entityOpt = jpaRepository.findById(flagName);
        if (entityOpt.isPresent()) {
            FeatureFlagEntity entity = entityOpt.get();
            entity.setEnabled(enabled);
            entity.setUpdatedAt(LocalDateTime.now());
            jpaRepository.save(entity);
            
            // Обновляем кэш
            FeatureFlag flag = entity.toModel();
            flag.setUpdatedBy(updatedBy);
            flagCache.put(flagName, flag);
            
            // Синхронизируем с Redis
            syncToRedis(flag);
        }
    }
    
    public Map<String, Object> getFeatureFlagStats(String flagName) {
        // Возвращаем базовую статистику
        Optional<FeatureFlagEntity> entityOpt = jpaRepository.findById(flagName);
        if (entityOpt.isPresent()) {
            FeatureFlagEntity entity = entityOpt.get();
            return Map.of(
                    "name", entity.getName(),
                    "enabled", entity.isEnabled(),
                    "createdAt", entity.getCreatedAt(),
                    "updatedAt", entity.getUpdatedAt(),
                    "usageCount", 0L // TODO: добавить счетчик использований
            );
        }
        return Map.of();
    }
    
    public void refreshCache() {
        log.info("Refreshing feature flags cache...");
        flagCache.clear();
        
        List<FeatureFlag> allFlags = getAllFeatureFlags();
        for (FeatureFlag flag : allFlags) {
            flagCache.put(flag.getName(), flag);
        }
        
        log.info("Feature flags cache refreshed. Loaded {} flags", allFlags.size());
        publishFeatureFlagEvent("CACHE_REFRESHED", null);
    }
    
    public void refreshFlag(String flagName) {
        Optional<FeatureFlag> flagOpt = getFeatureFlag(flagName);
        if (flagOpt.isPresent()) {
            flagCache.put(flagName, flagOpt.get());
            log.debug("Refreshed flag '{}' in cache", flagName);
        } else {
            flagCache.remove(flagName);
            log.debug("Removed flag '{}' from cache (not found in storage)", flagName);
        }
    }
    
    public int getCacheSize() {
        return flagCache.size();
    }
    
    public boolean isFlagCached(String flagName) {
        return flagCache.containsKey(flagName);
    }
    
    private void updateUsageStats(String flagName) {
        // TODO: Implement usage statistics tracking
        log.debug("Usage stats updated for flag: {}", flagName);
    }
    
    private void syncToRedis(FeatureFlag flag) {
        if (redisRepository != null) {
            try {
                redisRepository.save(flag);
                log.debug("Synced flag '{}' to Redis", flag.getName());
            } catch (Exception e) {
                log.warn("Failed to sync flag '{}' to Redis: {}", flag.getName(), e.getMessage());
            }
        }
    }
    
    private void deleteFromRedis(String flagName) {
        if (redisRepository != null) {
            try {
                redisRepository.delete(flagName);
                log.debug("Deleted flag '{}' from Redis", flagName);
            } catch (Exception e) {
                log.warn("Failed to delete flag '{}' from Redis: {}", flagName, e.getMessage());
            }
        }
    }
    
    private void publishFeatureFlagEvent(String eventType, FeatureFlag flag) {
        try {
            FeatureFlagEvent event = FeatureFlagEvent.builder()
                    .eventType(eventType)
                    .flagName(flag != null ? flag.getName() : null)
                    .flag(flag)
                    .timestamp(LocalDateTime.now())
                    .build();
            
            eventPublisher.publishEvent(event);
            log.debug("Published feature flag event: {} for flag '{}'", eventType, 
                flag != null ? flag.getName() : "N/A");
            
        } catch (Exception e) {
            log.error("Error publishing feature flag event: {}", e.getMessage());
        }
    }
    
    // Inner class для событий
    @lombok.Data
    @lombok.Builder
    public static class FeatureFlagEvent {
        private String eventType;
        private String flagName;
        private FeatureFlag flag;
        private LocalDateTime timestamp;
    }
}
