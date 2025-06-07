package shit.back.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shit.back.config.SecurityProperties;
import shit.back.service.TelegramBotService;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
public class HealthController {
    
    @Autowired
    private TelegramBotService telegramBotService;
    
    @Autowired
    private SecurityProperties securityProperties;
    
    @Autowired
    private shit.back.service.FeatureFlagService featureFlagService;
    
    @Value("${spring.application.name:TelegramStarManager}")
    private String applicationName;
    
    /**
     * Общий health check эндпоинт
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now());
        health.put("application", applicationName);
        health.put("version", "1.0.0");
        
        // Telegram Bot Status
        Map<String, Object> botHealth = new HashMap<>();
        botHealth.put("registered", telegramBotService.isBotRegistered());
        botHealth.put("status", telegramBotService.getBotStatus());
        botHealth.put("username", telegramBotService.getBotUsername());
        
        if (!telegramBotService.getErrorMessage().isEmpty()) {
            botHealth.put("error", telegramBotService.getErrorMessage());
        }
        
        health.put("telegram", botHealth);
        
        // Security Status
        Map<String, Object> securityHealth = new HashMap<>();
        securityHealth.put("apiProtection", securityProperties.getApi().isEnabled());
        securityHealth.put("rateLimiting", securityProperties.getRateLimit().isEnabled());
        securityHealth.put("validation", securityProperties.getValidation().isEnabled());
        securityHealth.put("cors", securityProperties.getCors().isEnabled());
        
        health.put("security", securityHealth);
        
        // Feature Flags Status
        Map<String, Object> featureFlagsHealth = new HashMap<>();
        try {
            featureFlagsHealth.put("totalFlags", featureFlagService.getAllFeatureFlags().size());
            featureFlagsHealth.put("activeFlags", featureFlagService.getActiveFeatureFlags().size());
            featureFlagsHealth.put("cacheSize", featureFlagService.getCacheSize());
            featureFlagsHealth.put("status", "operational");
        } catch (Exception e) {
            featureFlagsHealth.put("status", "error");
            featureFlagsHealth.put("error", e.getMessage());
        }
        health.put("featureFlags", featureFlagsHealth);
        
        // Overall application health
        boolean isHealthy = true; // App is healthy even if bot fails
        health.put("healthy", isHealthy);
        
        log.debug("Health check requested - Status: {}", isHealthy ? "HEALTHY" : "UNHEALTHY");
        
        return ResponseEntity.ok(health);
    }
    
    /**
     * Детальная диагностика Telegram бота
     */
    @GetMapping("/health/bot")
    public ResponseEntity<Map<String, Object>> getBotHealth() {
        Map<String, Object> status = new HashMap<>();
        
        status.put("registered", telegramBotService.isBotRegistered());
        status.put("status", telegramBotService.getBotStatus());
        status.put("username", telegramBotService.getBotUsername());
        status.put("timestamp", LocalDateTime.now());
        
        if (!telegramBotService.getErrorMessage().isEmpty()) {
            status.put("error", telegramBotService.getErrorMessage());
        }
        
        // Recommendations based on status
        if (!telegramBotService.isBotRegistered()) {
            status.put("recommendations", Map.of(
                "action", "Check bot configuration",
                "steps", new String[]{
                    "1. Verify TELEGRAM_BOT_TOKEN in .env file",
                    "2. Ensure bot exists and is active in @BotFather",
                    "3. Check bot username matches configuration",
                    "4. Restart application after fixing configuration"
                }
            ));
        }
        
        log.debug("Bot status requested - Registered: {}, Status: {}", 
            telegramBotService.isBotRegistered(), telegramBotService.getBotStatus());
        
        return ResponseEntity.ok(status);
    }
    
    /**
     * Статус системы безопасности
     */
    @GetMapping("/security/status")
    public ResponseEntity<Map<String, Object>> getSecurityStatus() {
        Map<String, Object> status = new HashMap<>();
        
        status.put("timestamp", LocalDateTime.now());
        
        // API Security
        Map<String, Object> apiSec = new HashMap<>();
        apiSec.put("enabled", securityProperties.getApi().isEnabled());
        apiSec.put("headerName", securityProperties.getApi().getHeaderName());
        status.put("api", apiSec);
        
        // Rate Limiting
        Map<String, Object> rateLimit = new HashMap<>();
        rateLimit.put("enabled", securityProperties.getRateLimit().isEnabled());
        rateLimit.put("userRequestsPerMinute", securityProperties.getRateLimit().getUserRequestsPerMinute());
        rateLimit.put("apiRequestsPerMinute", securityProperties.getRateLimit().getApiRequestsPerMinute());
        status.put("rateLimit", rateLimit);
        
        // Validation
        Map<String, Object> validation = new HashMap<>();
        validation.put("enabled", securityProperties.getValidation().isEnabled());
        validation.put("maxMessageLength", securityProperties.getValidation().getMaxMessageLength());
        validation.put("maxCallbackDataLength", securityProperties.getValidation().getMaxCallbackDataLength());
        validation.put("allowedCallbackPrefixes", securityProperties.getValidation().getAllowedCallbackPrefixes().size());
        status.put("validation", validation);
        
        // CORS
        Map<String, Object> cors = new HashMap<>();
        cors.put("enabled", securityProperties.getCors().isEnabled());
        cors.put("allowedOrigins", securityProperties.getCors().getAllowedOrigins().size());
        cors.put("allowedMethods", securityProperties.getCors().getAllowedMethods().size());
        status.put("cors", cors);
        
        log.debug("Security status requested");
        
        return ResponseEntity.ok(status);
    }
    
    /**
     * Простой ping эндпоинт для проверки доступности
     */
    @GetMapping("/ping")
    public ResponseEntity<Map<String, String>> ping() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "pong");
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }
}
