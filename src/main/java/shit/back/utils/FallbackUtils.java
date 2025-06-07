package shit.back.utils;

import lombok.extern.slf4j.Slf4j;
import shit.back.model.FeatureFlag;
import shit.back.service.FeatureFlagService;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Slf4j
public class FallbackUtils {
    
    /**
     * Выполняет операцию с fallback на пустой результат при ошибке
     */
    public static <T> T executeWithFallback(Supplier<T> operation, T fallbackValue, String operationName) {
        try {
            return operation.get();
        } catch (Exception e) {
            log.warn("Operation '{}' failed, using fallback: {}", operationName, e.getMessage());
            return fallbackValue;
        }
    }
    
    /**
     * Получает все флаги с fallback на пустой список
     */
    public static List<FeatureFlag> getAllFlagsWithFallback(FeatureFlagService service) {
        return executeWithFallback(
            service::getAllFeatureFlags,
            new ArrayList<>(),
            "getAllFeatureFlags"
        );
    }
    
    /**
     * Получает активные флаги с fallback на пустой список
     */
    public static List<FeatureFlag> getActiveFlagsWithFallback(FeatureFlagService service) {
        return executeWithFallback(
            service::getActiveFeatureFlags,
            new ArrayList<>(),
            "getActiveFeatureFlags"
        );
    }
    
    /**
     * Получает размер кэша с fallback на 0
     */
    public static int getCacheSizeWithFallback(FeatureFlagService service) {
        return executeWithFallback(
            service::getCacheSize,
            0,
            "getCacheSize"
        );
    }
    
    /**
     * Данные для админ панели с fallback логикой
     */
    public static class AdminData {
        public final List<FeatureFlag> allFlags;
        public final List<FeatureFlag> activeFlags;
        public final int cacheSize;
        public final int totalFlags;
        public final int activeFlagsCount;
        
        public AdminData(FeatureFlagService service) {
            this.allFlags = getAllFlagsWithFallback(service);
            this.activeFlags = getActiveFlagsWithFallback(service);
            this.cacheSize = getCacheSizeWithFallback(service);
            this.totalFlags = allFlags.size();
            this.activeFlagsCount = activeFlags.size();
        }
    }
}
