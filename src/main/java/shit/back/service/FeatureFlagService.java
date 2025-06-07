package shit.back.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import shit.back.model.FeatureFlag;
import shit.back.repository.FeatureFlagRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class FeatureFlagService {
    
    @Autowired
    private FeatureFlagRepository featureFlagRepository;
    
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
                    featureFlagRepository.updateUsageStats(flagName);
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
        
        // Загружаем из репозитория
        Optional<FeatureFlag> flagOpt = featureFlagRepository.findByName(flagName);
        if (flagOpt.isPresent()) {
            FeatureFlag flag = flagOpt.get();
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
        
        // Сохраняем в репозиторий
        featureFlagRepository.save(featureFlag);
        
        // Обновляем кэш
        flagCache.put(featureFlag.getName(), featureFlag);
        
        // Публикуем событие
        publishFeatureFlagEvent("CREATED", featureFlag);
        
        log.info("Feature flag '{}' created successfully", featureFlag.getName());
        return featureFlag;
    }
    
    public FeatureFlag updateFeatureFlag(String flagName, FeatureFlag updates) {
        Optional<FeatureFlag> existingOpt = featureFlagRepository.findByName(flagName);
        if (existingOpt.isEmpty()) {
            throw new RuntimeException("Feature flag '" + flagName + "' not found");
        }
        
        FeatureFlag existing = existingOpt.get();
        
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
        
        // Сохраняем
        featureFlagRepository.save(existing);
        
        // Обновляем кэш
        flagCache.put(flagName, existing);
        
        // Публикуем событие
        publishFeatureFlagEvent("UPDATED", existing);
        
        log.info("Feature flag '{}' updated successfully", flagName);
        return existing;
    }
    
    public void deleteFeatureFlag(String flagName) {
        Optional<FeatureFlag> flagOpt = featureFlagRepository.findByName(flagName);
        if (flagOpt.isEmpty()) {
            throw new RuntimeException("Feature flag '" + flagName + "' not found");
        }
        
        FeatureFlag flag = flagOpt.get();
        
        // Удаляем из репозитория
        featureFlagRepository.delete(flagName);
        
        // Удаляем из кэша
        flagCache.remove(flagName);
        
        // Публикуем событие
        publishFeatureFlagEvent("DELETED", flag);
        
        log.info("Feature flag '{}' deleted successfully", flagName);
    }
    
    public List<FeatureFlag> getAllFeatureFlags() {
        try {
            return featureFlagRepository.findAll();
        } catch (Exception e) {
            log.warn("Repository unavailable, returning cached flags: {}", e.getMessage());
            return new java.util.ArrayList<>(flagCache.values());
        }
    }
    
    public Optional<FeatureFlag> getFeatureFlag(String flagName) {
        try {
            return featureFlagRepository.findByName(flagName);
        } catch (Exception e) {
            log.warn("Repository unavailable, checking cache for flag '{}': {}", flagName, e.getMessage());
            return Optional.ofNullable(flagCache.get(flagName));
        }
    }
    
    public List<FeatureFlag> getActiveFeatureFlags() {
        try {
            return featureFlagRepository.findActiveFlags();
        } catch (Exception e) {
            log.warn("Repository unavailable, filtering cached flags: {}", e.getMessage());
            return flagCache.values().stream()
                .filter(FeatureFlag::isEnabled)
                .collect(java.util.stream.Collectors.toList());
        }
    }
    
    public List<FeatureFlag> getUserFeatureFlags(String userId) {
        return featureFlagRepository.findFlagsForUser(userId);
    }
    
    public void enableFeatureFlag(String flagName, String updatedBy) {
        featureFlagRepository.enableFlag(flagName, updatedBy);
        
        // Обновляем кэш
        Optional<FeatureFlag> flagOpt = featureFlagRepository.findByName(flagName);
        flagOpt.ifPresent(flag -> {
            flagCache.put(flagName, flag);
            publishFeatureFlagEvent("ENABLED", flag);
        });
    }
    
    public void disableFeatureFlag(String flagName, String updatedBy) {
        featureFlagRepository.disableFlag(flagName, updatedBy);
        
        // Обновляем кэш
        Optional<FeatureFlag> flagOpt = featureFlagRepository.findByName(flagName);
        flagOpt.ifPresent(flag -> {
            flagCache.put(flagName, flag);
            publishFeatureFlagEvent("DISABLED", flag);
        });
    }
    
    public void toggleFeatureFlag(String flagName, String updatedBy) {
        featureFlagRepository.toggleFlag(flagName, updatedBy);
        
        // Обновляем кэш
        Optional<FeatureFlag> flagOpt = featureFlagRepository.findByName(flagName);
        flagOpt.ifPresent(flag -> {
            flagCache.put(flagName, flag);
            publishFeatureFlagEvent("TOGGLED", flag);
        });
    }
    
    public Map<String, Object> getFeatureFlagStats(String flagName) {
        return featureFlagRepository.getUsageStats(flagName);
    }
    
    public void refreshCache() {
        log.info("Refreshing feature flags cache...");
        flagCache.clear();
        
        List<FeatureFlag> allFlags = featureFlagRepository.findAll();
        for (FeatureFlag flag : allFlags) {
            flagCache.put(flag.getName(), flag);
        }
        
        log.info("Feature flags cache refreshed. Loaded {} flags", allFlags.size());
        publishFeatureFlagEvent("CACHE_REFRESHED", null);
    }
    
    public void refreshFlag(String flagName) {
        Optional<FeatureFlag> flagOpt = featureFlagRepository.findByName(flagName);
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
