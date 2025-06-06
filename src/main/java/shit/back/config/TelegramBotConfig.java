package shit.back.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
@Configuration
public class TelegramBotConfig {
    
    @Bean
    public TelegramBotsApi telegramBotsApi() {
        try {
            TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
            log.info("TelegramBotsApi created successfully");
            return api;
        } catch (TelegramApiException e) {
            log.error("Error creating TelegramBotsApi: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create TelegramBotsApi", e);
        }
    }
}
