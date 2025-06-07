package shit.back.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import shit.back.model.FeatureFlag;
import shit.back.service.FeatureFlagService;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/feature-flags")
public class FeatureFlagController {
    
    @Autowired
    private FeatureFlagService featureFlagService;
    
    @GetMapping
    public ResponseEntity<List<FeatureFlag>> getAllFeatureFlags() {
        try {
            List<FeatureFlag> flags = featureFlagService.getAllFeatureFlags();
            return ResponseEntity.ok(flags);
        } catch (Exception e) {
            log.error("Error retrieving feature flags: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/{flagName}")
    public ResponseEntity<FeatureFlag> getFeatureFlag(@PathVariable String flagName) {
        try {
            Optional<FeatureFlag> flag = featureFlagService.getFeatureFlag(flagName);
            return flag.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error retrieving feature flag '{}': {}", flagName, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/{flagName}/enabled")
    public ResponseEntity<Map<String, Object>> checkFeatureFlag(
            @PathVariable String flagName,
            @RequestParam(required = false) String userId) {
        try {
            boolean enabled = featureFlagService.isFeatureEnabled(flagName, userId);
            
            Map<String, Object> response = Map.of(
                "flagName", flagName,
                "enabled", enabled,
                "userId", userId != null ? userId : "global",
                "timestamp", LocalDateTime.now()
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error checking feature flag '{}': {}", flagName, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/active")
    public ResponseEntity<List<FeatureFlag>> getActiveFeatureFlags() {
        try {
            List<FeatureFlag> activeFlags = featureFlagService.getActiveFeatureFlags();
            return ResponseEntity.ok(activeFlags);
        } catch (Exception e) {
            log.error("Error retrieving active feature flags: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<FeatureFlag>> getUserFeatureFlags(@PathVariable String userId) {
        try {
            List<FeatureFlag> userFlags = featureFlagService.getUserFeatureFlags(userId);
            return ResponseEntity.ok(userFlags);
        } catch (Exception e) {
            log.error("Error retrieving feature flags for user '{}': {}", userId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping
    public ResponseEntity<FeatureFlag> createFeatureFlag(
            @Valid @RequestBody CreateFeatureFlagRequest request) {
        try {
            FeatureFlag featureFlag = FeatureFlag.builder()
                    .name(request.getName())
                    .description(request.getDescription())
                    .enabled(request.isEnabled())
                    .rolloutPercentage(request.getRolloutPercentage())
                    .enabledForUsers(request.getEnabledForUsers())
                    .enabledFrom(request.getEnabledFrom())
                    .enabledUntil(request.getEnabledUntil())
                    .environment(request.getEnvironment())
                    .version(request.getVersion())
                    .requiresRestart(request.isRequiresRestart())
                    .fallbackMethod(request.getFallbackMethod())
                    .createdBy(request.getCreatedBy())
                    .build();
            
            FeatureFlag created = featureFlagService.createFeatureFlag(featureFlag);
            return ResponseEntity.ok(created);
            
        } catch (Exception e) {
            log.error("Error creating feature flag: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    @PutMapping("/{flagName}")
    public ResponseEntity<FeatureFlag> updateFeatureFlag(
            @PathVariable String flagName,
            @Valid @RequestBody UpdateFeatureFlagRequest request) {
        try {
            FeatureFlag updates = FeatureFlag.builder()
                    .description(request.getDescription())
                    .enabled(request.getEnabled() != null ? request.getEnabled() : false)
                    .rolloutPercentage(request.getRolloutPercentage())
                    .enabledForUsers(request.getEnabledForUsers())
                    .enabledFrom(request.getEnabledFrom())
                    .enabledUntil(request.getEnabledUntil())
                    .updatedBy(request.getUpdatedBy())
                    .build();
            
            FeatureFlag updated = featureFlagService.updateFeatureFlag(flagName, updates);
            return ResponseEntity.ok(updated);
            
        } catch (RuntimeException e) {
            log.error("Error updating feature flag '{}': {}", flagName, e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error updating feature flag '{}': {}", flagName, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping("/{flagName}/enable")
    public ResponseEntity<Map<String, Object>> enableFeatureFlag(
            @PathVariable String flagName,
            @RequestParam(required = false, defaultValue = "API") String updatedBy) {
        try {
            featureFlagService.enableFeatureFlag(flagName, updatedBy);
            
            Map<String, Object> response = Map.of(
                "flagName", flagName,
                "action", "enabled",
                "updatedBy", updatedBy,
                "timestamp", LocalDateTime.now()
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error enabling feature flag '{}': {}", flagName, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping("/{flagName}/disable")
    public ResponseEntity<Map<String, Object>> disableFeatureFlag(
            @PathVariable String flagName,
            @RequestParam(required = false, defaultValue = "API") String updatedBy) {
        try {
            featureFlagService.disableFeatureFlag(flagName, updatedBy);
            
            Map<String, Object> response = Map.of(
                "flagName", flagName,
                "action", "disabled",
                "updatedBy", updatedBy,
                "timestamp", LocalDateTime.now()
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error disabling feature flag '{}': {}", flagName, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping("/{flagName}/toggle")
    public ResponseEntity<Map<String, Object>> toggleFeatureFlag(
            @PathVariable String flagName,
            @RequestParam(required = false, defaultValue = "API") String updatedBy) {
        try {
            featureFlagService.toggleFeatureFlag(flagName, updatedBy);
            
            // Получаем обновленное состояние
            Optional<FeatureFlag> flagOpt = featureFlagService.getFeatureFlag(flagName);
            boolean currentState = flagOpt.map(FeatureFlag::isEnabled).orElse(false);
            
            Map<String, Object> response = Map.of(
                "flagName", flagName,
                "action", "toggled",
                "currentState", currentState,
                "updatedBy", updatedBy,
                "timestamp", LocalDateTime.now()
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error toggling feature flag '{}': {}", flagName, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @DeleteMapping("/{flagName}")
    public ResponseEntity<Map<String, Object>> deleteFeatureFlag(@PathVariable String flagName) {
        try {
            featureFlagService.deleteFeatureFlag(flagName);
            
            Map<String, Object> response = Map.of(
                "flagName", flagName,
                "action", "deleted",
                "timestamp", LocalDateTime.now()
            );
            
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Error deleting feature flag '{}': {}", flagName, e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error deleting feature flag '{}': {}", flagName, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/{flagName}/stats")
    public ResponseEntity<Map<String, Object>> getFeatureFlagStats(@PathVariable String flagName) {
        try {
            Map<String, Object> stats = featureFlagService.getFeatureFlagStats(flagName);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error retrieving stats for feature flag '{}': {}", flagName, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping("/refresh-cache")
    public ResponseEntity<Map<String, Object>> refreshCache() {
        try {
            featureFlagService.refreshCache();
            
            Map<String, Object> response = Map.of(
                "action", "cache_refreshed",
                "cacheSize", featureFlagService.getCacheSize(),
                "timestamp", LocalDateTime.now()
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error refreshing feature flags cache: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    // DTOs для запросов
    @lombok.Data
    public static class CreateFeatureFlagRequest {
        private String name;
        private String description;
        private boolean enabled = false;
        private Integer rolloutPercentage;
        private java.util.Set<String> enabledForUsers;
        private LocalDateTime enabledFrom;
        private LocalDateTime enabledUntil;
        private String environment = "development";
        private String version = "1.0";
        private boolean requiresRestart = false;
        private String fallbackMethod;
        private String createdBy = "API";
    }
    
    @lombok.Data
    public static class UpdateFeatureFlagRequest {
        private String description;
        private Boolean enabled;
        private Integer rolloutPercentage;
        private java.util.Set<String> enabledForUsers;
        private LocalDateTime enabledFrom;
        private LocalDateTime enabledUntil;
        private String updatedBy = "API";
    }
}
