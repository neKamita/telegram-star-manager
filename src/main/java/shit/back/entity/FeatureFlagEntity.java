package shit.back.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import shit.back.model.FeatureFlag;

import java.time.LocalDateTime;

@Entity
@Table(name = "feature_flags")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatureFlagEntity {
    
    @Id
    @Column(name = "name", nullable = false, unique = true)
    private String name;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "enabled", nullable = false)
    private boolean enabled;
    
    @Column(name = "rollout_percentage")
    private Integer rolloutPercentage;
    
    @Column(name = "environment")
    private String environment;
    
    @Column(name = "version")
    private String version;
    
    @Column(name = "created_by")
    private String createdBy;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "enabled_until")
    private LocalDateTime enabledUntil;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    /**
     * Convert to FeatureFlag model
     */
    public FeatureFlag toModel() {
        return FeatureFlag.builder()
                .name(this.name)
                .description(this.description)
                .enabled(this.enabled)
                .rolloutPercentage(this.rolloutPercentage)
                .environment(this.environment)
                .version(this.version)
                .createdBy(this.createdBy)
                .createdAt(this.createdAt)
                .updatedAt(this.updatedAt)
                .enabledUntil(this.enabledUntil)
                .build();
    }
    
    /**
     * Create from FeatureFlag model
     */
    public static FeatureFlagEntity fromModel(FeatureFlag flag) {
        return FeatureFlagEntity.builder()
                .name(flag.getName())
                .description(flag.getDescription())
                .enabled(flag.isEnabled())
                .rolloutPercentage(flag.getRolloutPercentage())
                .environment(flag.getEnvironment())
                .version(flag.getVersion())
                .createdBy(flag.getCreatedBy())
                .createdAt(flag.getCreatedAt())
                .updatedAt(flag.getUpdatedAt())
                .enabledUntil(flag.getEnabledUntil())
                .build();
    }
}
