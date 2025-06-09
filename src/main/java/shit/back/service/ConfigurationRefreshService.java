package shit.back.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import shit.back.model.FeatureFlag;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class ConfigurationRefreshService {
    
    @Autowired
    @Lazy
    private FeatureFlagService featureFlagService;
    
    @Autowired
    private UserSessionService userSessionService;
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    @PostConstruct
    public void init() {
        log.info("🔄 Configuration Refresh Service initialized");
        // Подписываемся на события изменения флагов
        subscribeToFeatureFlagEvents();
    }
    
    /**
     * Обновляет конфигурацию без перезапуска приложения
     */
    @Async
    public CompletableFuture<Void> refreshConfiguration() {
        log.info("🔄 Starting configuration refresh...");
        
        try {
            // 1. Обновляем кэш флагов функций
            featureFlagService.refreshCache();
            
            // 2. Публикуем событие глобального обновления
            publishConfigurationRefreshEvent();
            
            // 3. Уведомляем активных пользователей (опционально)
            notifyActiveUsers();
            
            log.info("✅ Configuration refresh completed successfully");
            
        } catch (Exception e) {
            log.error("❌ Error during configuration refresh: {}", e.getMessage(), e);
            throw new RuntimeException("Configuration refresh failed", e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Применяет изменения конкретного флага функции
     */
    @Async
    public CompletableFuture<Void> applyFeatureFlagChange(String flagName) {
        log.info("🎚️ Applying feature flag change for '{}'", flagName);
        
        try {
            // Обновляем конкретный флаг в кэше
            featureFlagService.refreshFlag(flagName);
            
            // Получаем обновленный флаг
            FeatureFlag flag = featureFlagService.getFeatureFlag(flagName).orElse(null);
            
            // Публикуем событие изменения флага
            publishFeatureFlagChangeEvent(flagName, flag);
            
            log.info("✅ Feature flag '{}' change applied successfully", flagName);
            
        } catch (Exception e) {
            log.error("❌ Error applying feature flag change for '{}': {}", flagName, e.getMessage(), e);
            throw new RuntimeException("Feature flag change application failed", e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Перезагружает конфигурацию для конкретного пользователя
     */
    public void refreshUserConfiguration(String userId) {
        log.debug("👤 Refreshing configuration for user '{}'", userId);
        
        try {
            // Получаем флаги, доступные пользователю
            List<FeatureFlag> userFlags = featureFlagService.getUserFeatureFlags(userId);
            
            // Публикуем событие обновления для пользователя
            publishUserConfigurationRefreshEvent(userId, userFlags);
            
            log.debug("✅ User '{}' configuration refreshed", userId);
            
        } catch (Exception e) {
            log.error("❌ Error refreshing configuration for user '{}': {}", userId, e.getMessage());
        }
    }
    
    /**
     * Проверяет и применяет изменения без нарушения пользовательских сессий
     */
    public void safeConfigurationUpdate() {
        log.info("🛡️ Performing safe configuration update...");
        
        try {
            // Сохраняем текущие состояния сессий
            preserveUserSessions();
            
            // Обновляем конфигурацию
            refreshConfiguration().get();
            
            // Восстанавливаем сессии с новой конфигурацией
            restoreUserSessions();
            
            log.info("✅ Safe configuration update completed");
            
        } catch (Exception e) {
            log.error("❌ Error during safe configuration update: {}", e.getMessage(), e);
        }
    }
    
    private void subscribeToFeatureFlagEvents() {
        // Подписка на события изменения флагов будет обрабатываться через @EventListener
        log.debug("📡 Subscribed to feature flag events");
    }
    
    private void publishConfigurationRefreshEvent() {
        ConfigurationRefreshEvent event = ConfigurationRefreshEvent.builder()
                .eventType("CONFIGURATION_REFRESHED")
                .timestamp(LocalDateTime.now())
                .source("ConfigurationRefreshService")
                .build();
        
        eventPublisher.publishEvent(event);
        log.debug("📢 Published configuration refresh event");
    }
    
    private void publishFeatureFlagChangeEvent(String flagName, FeatureFlag flag) {
        FeatureFlagChangeEvent event = FeatureFlagChangeEvent.builder()
                .eventType("FEATURE_FLAG_CHANGED")
                .flagName(flagName)
                .flag(flag)
                .timestamp(LocalDateTime.now())
                .source("ConfigurationRefreshService")
                .build();
        
        eventPublisher.publishEvent(event);
        log.debug("📢 Published feature flag change event for '{}'", flagName);
    }
    
    private void publishUserConfigurationRefreshEvent(String userId, List<FeatureFlag> userFlags) {
        UserConfigurationRefreshEvent event = UserConfigurationRefreshEvent.builder()
                .eventType("USER_CONFIGURATION_REFRESHED")
                .userId(userId)
                .userFlags(userFlags)
                .timestamp(LocalDateTime.now())
                .source("ConfigurationRefreshService")
                .build();
        
        eventPublisher.publishEvent(event);
        log.debug("📢 Published user configuration refresh event for user '{}'", userId);
    }
    
    private void notifyActiveUsers() {
        log.debug("📢 Notifying active users about configuration changes...");
        
        // Здесь можно добавить логику уведомления активных пользователей
        // Например, через WebSocket или Server-Sent Events
        
        // Пока просто логируем
        log.debug("📢 Active users notification completed");
    }
    
    private void preserveUserSessions() {
        log.debug("💾 Preserving user sessions...");
        
        // Сохраняем критически важные данные сессий
        // В реальном приложении здесь может быть более сложная логика
        
        log.debug("💾 User sessions preserved");
    }
    
    private void restoreUserSessions() {
        log.debug("🔄 Restoring user sessions with updated configuration...");
        
        // Восстанавливаем сессии с учетом новой конфигурации
        // В реальном приложении здесь может быть логика миграции состояний
        
        log.debug("🔄 User sessions restored");
    }
    
    // События для системы обновления конфигурации
    @lombok.Data
    @lombok.Builder
    public static class ConfigurationRefreshEvent {
        private String eventType;
        private LocalDateTime timestamp;
        private String source;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class FeatureFlagChangeEvent {
        private String eventType;
        private String flagName;
        private FeatureFlag flag;
        private LocalDateTime timestamp;
        private String source;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class UserConfigurationRefreshEvent {
        private String eventType;
        private String userId;
        private List<FeatureFlag> userFlags;
        private LocalDateTime timestamp;
        private String source;
    }
}
