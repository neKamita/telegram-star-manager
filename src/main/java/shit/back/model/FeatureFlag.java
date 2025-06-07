package shit.back.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureFlag {
    
    private String name;
    private String description;
    private boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    
    // Расширенные настройки
    private Integer rolloutPercentage; // Процент пользователей (0-100)
    private Set<String> enabledForUsers; // Конкретные пользователи
    private Set<String> enabledForGroups; // Группы пользователей
    private LocalDateTime enabledFrom; // Время включения
    private LocalDateTime enabledUntil; // Время отключения
    
    // Метаданные
    private String environment; // development, production, staging
    private String version; // Версия приложения
    private boolean requiresRestart; // Требует ли перезапуск
    private String fallbackMethod; // Метод fallback
    private boolean experimental; // Экспериментальная функция
    private boolean userSpecific; // Функция зависит от пользователя
    
    // Статистика
    private Long usageCount;
    private LocalDateTime lastUsed;
    
    public boolean isEnabledForUser(String userId) {
        if (!enabled) {
            return false;
        }
        
        // Проверяем временные ограничения
        LocalDateTime now = LocalDateTime.now();
        if (enabledFrom != null && now.isBefore(enabledFrom)) {
            return false;
        }
        if (enabledUntil != null && now.isAfter(enabledUntil)) {
            return false;
        }
        
        // Проверяем конкретных пользователей
        if (enabledForUsers != null && !enabledForUsers.isEmpty()) {
            return enabledForUsers.contains(userId);
        }
        
        // Проверяем процентный rollout
        if (rolloutPercentage != null && rolloutPercentage < 100) {
            return userId.hashCode() % 100 < rolloutPercentage;
        }
        
        return true;
    }
    
    public boolean isActive() {
        LocalDateTime now = LocalDateTime.now();
        
        if (enabledFrom != null && now.isBefore(enabledFrom)) {
            return false;
        }
        
        if (enabledUntil != null && now.isAfter(enabledUntil)) {
            return false;
        }
        
        return enabled;
    }
    
    public void updateUsageStats() {
        this.usageCount = (this.usageCount == null ? 0 : this.usageCount) + 1;
        this.lastUsed = LocalDateTime.now();
    }
}
