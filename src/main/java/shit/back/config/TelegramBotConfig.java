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
        String activeProfiles = System.getProperty("spring.profiles.active", "default");
        boolean isProduction = activeProfiles.contains("production");
        
        log.info("🔄 TelegramBotConfig.registerBots() вызван");
        log.info("📋 Активные профили: {}", activeProfiles);
        log.info("🎯 Режим работы: {}", isProduction ? "PRODUCTION (Webhook)" : "DEVELOPMENT (Polling)");
        
        try {
            if (!isProduction) {
                // Development/staging mode - используем polling
                if (telegramBotService != null) {
                    log.info("🤖 Development mode: Регистрация TelegramBotService (Long Polling)");
                    TelegramBotsApi api = telegramBotsApiForDevelopment();
                    if (api != null) {
                        try {
                            api.registerBot(telegramBotService);
                            telegramBotService.markAsRegistered();
                            log.info("✅ TelegramBotService успешно зарегистрирован для development");
                        } catch (TelegramApiException e) {
                            telegramBotService.markRegistrationFailed(e.getMessage());
                            log.error("❌ Ошибка регистрации TelegramBotService: {}", e.getMessage());
                            
                            if (e.getMessage().contains("can't use getUpdates method while webhook is active")) {
                                log.error("💡 РЕШЕНИЕ: Webhook все еще активен! TelegramBotService попытается его удалить автоматически");
                                log.error("💡 Если проблема сохраняется, вручную удалите webhook через Bot API");
                            }
                        }
                    } else {
                        log.error("❌ Не удалось создать TelegramBotsApi для development");
                    }
                } else {
                    log.warn("⚠️ TelegramBotService не найден для development режима!");
                    log.warn("💡 Проверьте, что профиль не содержит 'production'");
                }
                
                // Предупреждаем, если webhook сервис активен в dev режиме
                if (telegramWebhookBotService != null) {
                    log.warn("⚠️ TelegramWebhookBotService активен в development режиме!");
                    log.warn("💡 Убедитесь, что профиль настроен корректно");
                }
                
            } else {
                // Production mode - используем webhook
                if (telegramWebhookBotService != null) {
                    log.info("🤖 Production mode: TelegramWebhookBotService активен");
                    log.info("📌 Webhook bots работают независимо от TelegramBotsApi");
                    log.info("🌐 Webhook URL: {}", telegramWebhookBotService.getBotPath());
                    log.info("👤 Bot Username: {}", telegramWebhookBotService.getBotUsername());
                    log.info("✅ TelegramWebhookBotService готов к приему webhook запросов");
                } else {
                    log.error("❌ TelegramWebhookBotService НЕ НАЙДЕН в production режиме!");
                    log.error("🔍 Проверьте настройки профиля 'production'");
                }
                
                // Предупреждаем, если polling сервис активен в prod режиме
                if (telegramBotService != null) {
                    log.warn("⚠️ TelegramBotService активен в production режиме!");
                    log.warn("💡 В production должен использоваться только webhook");
                }
            }
            
        } catch (Exception e) {
            log.error("💥 Критическая ошибка при регистрации ботов: {}", e.getMessage(), e);
            
            if (isProduction && telegramWebhookBotService != null) {
                log.warn("🔄 В production режиме продолжаем работу с webhook");
            } else {
                log.error("💥 Не удалось настроить бота для текущего режима");
            }
        }
        
        log.info("🏁 TelegramBotConfig.registerBots() завершен");
    }
}
