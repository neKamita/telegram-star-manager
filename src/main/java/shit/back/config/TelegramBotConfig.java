package shit.back.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import shit.back.service.TelegramBotService;
import shit.back.service.TelegramWebhookBotService;

import jakarta.annotation.PostConstruct;

@Slf4j
@Configuration
public class TelegramBotConfig {
    
    @Autowired(required = false)
    private TelegramBotService telegramBotService;
    
    @Autowired(required = false)
    private TelegramWebhookBotService telegramWebhookBotService;
    
    @Bean
    @Profile("!production")
    public TelegramBotsApi telegramBotsApiForDevelopment() {
        try {
            TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
            log.info("TelegramBotsApi created for development/staging environment");
            return api;
        } catch (TelegramApiException e) {
            log.error("Error creating TelegramBotsApi: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create TelegramBotsApi", e);
        }
    }
    
    @Bean
    @Profile("production")
    public TelegramBotsApi telegramBotsApiForProduction() {
        try {
            TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
            log.info("TelegramBotsApi created for production environment (webhook mode)");
            return api;
        } catch (TelegramApiException e) {
            log.error("Error creating TelegramBotsApi: {}", e.getMessage(), e);
            // In production, don't throw exception - let webhook work independently
            log.warn("⚠️  TelegramBotsApi creation failed, but webhook can still work");
            return null;
        }
    }
    
    @PostConstruct
    public void registerBots() {
        log.info("🔄 TelegramBotConfig.registerBots() вызван");
        log.info("📋 Доступные профили: {}", System.getProperty("spring.profiles.active"));
        
        try {
            // Register long polling bot for development
            if (telegramBotService != null) {
                log.info("🤖 Development mode: Using TelegramBotService (Long Polling)");
                TelegramBotsApi api = telegramBotsApiForDevelopment();
                if (api != null) {
                    api.registerBot(telegramBotService);
                    log.info("✅ TelegramBotService зарегистрирован для development");
                }
            } else {
                log.info("❌ TelegramBotService не найден (нормально для production)");
            }
            
            // Webhook bot for production - does NOT need registration in TelegramBotsApi
            if (telegramWebhookBotService != null) {
                log.info("🤖 Production mode: Using TelegramWebhookBotService (Webhook)");
                log.info("📌 Webhook bots работают независимо от TelegramBotsApi");
                log.info("🌐 Webhook URL: {}", telegramWebhookBotService.getBotPath());
                log.info("👤 Bot Username: {}", telegramWebhookBotService.getBotUsername());
                log.info("✅ TelegramWebhookBotService готов к приему webhook запросов");
            } else {
                log.error("❌ TelegramWebhookBotService НЕ НАЙДЕН! Проверьте профиль и конфигурацию");
                log.error("🔍 Текущий профиль: {}", System.getProperty("spring.profiles.active"));
            }
            
        } catch (Exception e) {
            log.error("💥 Ошибка при регистрации ботов: {}", e.getMessage(), e);
            // В production не падаем, webhook может работать независимо
            if (telegramWebhookBotService != null) {
                log.warn("🔄 Продолжаем работу с webhook без регистрации в API");
            }
        }
        
        log.info("🏁 TelegramBotConfig.registerBots() завершен");
    }
}
