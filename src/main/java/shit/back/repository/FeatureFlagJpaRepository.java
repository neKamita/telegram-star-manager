package shit.back.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import shit.back.entity.FeatureFlagEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FeatureFlagJpaRepository extends JpaRepository<FeatureFlagEntity, String> {
    
    /**
     * Find all enabled feature flags
     */
    List<FeatureFlagEntity> findByEnabledTrue();
    
    /**
     * Find feature flags by environment
     */
    List<FeatureFlagEntity> findByEnvironment(String environment);
    
    /**
     * Find enabled feature flags by environment
     */
    List<FeatureFlagEntity> findByEnabledTrueAndEnvironment(String environment);
    
    /**
     * Find feature flags that are currently active (enabled and not expired)
     */
    @Query("SELECT f FROM FeatureFlagEntity f WHERE f.enabled = true AND (f.enabledUntil IS NULL OR f.enabledUntil > :now)")
    List<FeatureFlagEntity> findActiveFlags(LocalDateTime now);
    
    /**
     * Find expired feature flags
     */
    @Query("SELECT f FROM FeatureFlagEntity f WHERE f.enabledUntil IS NOT NULL AND f.enabledUntil <= :now")
    List<FeatureFlagEntity> findExpiredFlags(LocalDateTime now);
    
    /**
     * Count total feature flags
     */
    long count();
    
    /**
     * Count enabled feature flags
     */
    long countByEnabledTrue();
    
    /**
     * Find feature flags created by specific user
     */
    List<FeatureFlagEntity> findByCreatedBy(String createdBy);
    
    /**
     * Find feature flags created after specific date
     */
    List<FeatureFlagEntity> findByCreatedAtAfter(LocalDateTime date);
    
    /**
     * Check if feature flag exists by name
     */
    boolean existsByName(String name);
}
