package shit.back.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import shit.back.service.ConfigurationRefreshService;
import shit.back.service.FeatureFlagService.FeatureFlagEvent;

@Slf4j
@Component
public class FeatureFlagEventListener {
    
    @Autowired
    @Lazy
    private ConfigurationRefreshService configurationRefreshService;
    
    @EventListener
    @Async
    public void handleFeatureFlagEvent(FeatureFlagEvent event) {
        log.debug("🎯 Handling feature flag event: {} for flag '{}'", 
            event.getEventType(), event.getFlagName());
        
        try {
            switch (event.getEventType()) {
                case "CREATED":
                    handleFeatureFlagCreated(event);
                    break;
                case "UPDATED":
                    handleFeatureFlagUpdated(event);
                    break;
                case "ENABLED":
                    handleFeatureFlagEnabled(event);
                    break;
                case "DISABLED":
                    handleFeatureFlagDisabled(event);
                    break;
                case "TOGGLED":
                    handleFeatureFlagToggled(event);
                    break;
                case "DELETED":
                    handleFeatureFlagDeleted(event);
                    break;
                case "CACHE_REFRESHED":
                    handleCacheRefreshed(event);
                    break;
                default:
                    log.debug("⚠️ Unknown feature flag event type: {}", event.getEventType());
            }
            
        } catch (Exception e) {
            log.error("❌ Error handling feature flag event '{}' for flag '{}': {}", 
                event.getEventType(), event.getFlagName(), e.getMessage(), e);
        }
    }
    
    private void handleFeatureFlagCreated(FeatureFlagEvent event) {
        log.info("✨ Feature flag '{}' created, applying changes", event.getFlagName());
        configurationRefreshService.applyFeatureFlagChange(event.getFlagName());
    }
    
    private void handleFeatureFlagUpdated(FeatureFlagEvent event) {
        log.info("🔄 Feature flag '{}' updated, applying changes", event.getFlagName());
        configurationRefreshService.applyFeatureFlagChange(event.getFlagName());
    }
    
    private void handleFeatureFlagEnabled(FeatureFlagEvent event) {
        log.info("✅ Feature flag '{}' enabled, applying changes", event.getFlagName());
        configurationRefreshService.applyFeatureFlagChange(event.getFlagName());
        
        // Дополнительные действия при включении флага
        logFeatureFlagActivation(event);
    }
    
    private void handleFeatureFlagDisabled(FeatureFlagEvent event) {
        log.info("❌ Feature flag '{}' disabled, applying changes", event.getFlagName());
        configurationRefreshService.applyFeatureFlagChange(event.getFlagName());
        
        // Дополнительные действия при отключении флага
        logFeatureFlagDeactivation(event);
    }
    
    private void handleFeatureFlagToggled(FeatureFlagEvent event) {
        boolean isEnabled = event.getFlag() != null && event.getFlag().isEnabled();
        log.info("🔄 Feature flag '{}' toggled to {}, applying changes", 
            event.getFlagName(), isEnabled ? "enabled" : "disabled");
        
        configurationRefreshService.applyFeatureFlagChange(event.getFlagName());
    }
    
    private void handleFeatureFlagDeleted(FeatureFlagEvent event) {
        log.info("🗑️ Feature flag '{}' deleted, cleaning up", event.getFlagName());
        
        // При удалении флага можно выполнить дополнительную очистку
        // Например, уведомить активных пользователей о недоступности функции
    }
    
    private void handleCacheRefreshed(FeatureFlagEvent event) {
        log.info("🔄 Feature flags cache refreshed, configuration updated");
        
        // Можно выполнить дополнительные действия при обновлении кэша
        // Например, уведомить мониторинг или логирование
    }
    
    private void logFeatureFlagActivation(FeatureFlagEvent event) {
        if (event.getFlag() != null) {
            log.info("📊 Feature '{}' activated: description='{}', rollout={}%", 
                event.getFlagName(),
                event.getFlag().getDescription(),
                event.getFlag().getRolloutPercentage() != null ? 
                    event.getFlag().getRolloutPercentage() : 100
            );
        }
    }
    
    private void logFeatureFlagDeactivation(FeatureFlagEvent event) {
        if (event.getFlag() != null) {
            log.info("📊 Feature '{}' deactivated: description='{}', usage_count={}", 
                event.getFlagName(),
                event.getFlag().getDescription(),
                event.getFlag().getUsageCount() != null ? 
                    event.getFlag().getUsageCount() : 0
            );
        }
    }
    
    @EventListener
    @Async
    public void handleConfigurationRefreshEvent(ConfigurationRefreshService.ConfigurationRefreshEvent event) {
        log.info("🔄 Configuration refresh event received: {}", event.getEventType());
        
        // Здесь можно добавить дополнительную логику при обновлении конфигурации
        // Например, уведомление внешних систем или обновление кэшей
    }
    
    @EventListener
    @Async
    public void handleFeatureFlagChangeEvent(ConfigurationRefreshService.FeatureFlagChangeEvent event) {
        log.debug("🎚️ Feature flag change event received for '{}'", event.getFlagName());
        
        // Дополнительная обработка изменений флагов
        // Например, логирование в аудит или уведомление мониторинга
    }
    
    @EventListener
    @Async
    public void handleUserConfigurationRefreshEvent(ConfigurationRefreshService.UserConfigurationRefreshEvent event) {
        log.debug("👤 User configuration refresh event for user '{}', flags count: {}", 
            event.getUserId(), 
            event.getUserFlags() != null ? event.getUserFlags().size() : 0
        );
        
        // Здесь можно добавить логику персонализированного обновления
        // Например, отправка уведомлений пользователю о новых доступных функциях
    }
}
