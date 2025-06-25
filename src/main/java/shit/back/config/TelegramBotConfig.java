package shit.back.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * Конфигурация для создания бинов TelegramBotsApi.
 * Регистрация ботов вынесена в отдельный сервис для избежания циклических
 * зависимостей.
 */
@Slf4j
@Configuration
public class TelegramBotConfig {

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
}
