package shit.back.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Сервис для регистрации Telegram ботов после полной инициализации приложения.
 * Использует ApplicationReadyEvent для избежания циклических зависимостей.
 */
@Slf4j
@Service
public class TelegramBotRegistrationService {

    @Autowired(required = false)
    private TelegramBotService telegramBotService;

    @Autowired(required = false)
    private TelegramWebhookBotService telegramWebhookBotService;

    @Autowired(required = false)
    private TelegramBotsApi telegramBotsApi;

    /**
     * Регистрирует ботов после полного запуска приложения.
     * Выполняется асинхронно после инициализации всех бинов.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerBotsAfterStartup() {
        String activeProfiles = System.getProperty("spring.profiles.active", "default");
        boolean isProduction = activeProfiles.contains("production");

        log.info("🔄 TelegramBotRegistrationService.registerBotsAfterStartup() запущен");
        log.info("📋 Активные профили: {}", activeProfiles);
        log.info("🎯 Режим работы: {}", isProduction ? "PRODUCTION (Webhook)" : "DEVELOPMENT (Polling)");

        try {
            if (!isProduction) {
                registerDevelopmentBot();
            } else {
                registerProductionBot();
            }
        } catch (Exception e) {
            log.error("💥 Критическая ошибка при регистрации ботов: {}", e.getMessage(), e);
        }

        log.info("🏁 TelegramBotRegistrationService.registerBotsAfterStartup() завершен");
    }

    private void registerDevelopmentBot() {
        log.info("🤖 Development mode: начинаем регистрацию TelegramBotService (Long Polling)");

        if (telegramBotService == null) {
            log.warn("⚠️ TelegramBotService не найден для development режима!");
            log.warn("💡 Проверьте, что профиль не содержит 'production'");
            return;
        }

        if (telegramBotsApi == null) {
            log.error("❌ TelegramBotsApi не найден для development режима!");
            return;
        }

        // Инициализируем бот перед регистрацией
        telegramBotService.init();

        try {
            telegramBotsApi.registerBot(telegramBotService);
            telegramBotService.markAsRegistered();
            log.info("✅ TelegramBotService успешно зарегистрирован для development");
        } catch (TelegramApiException e) {
            telegramBotService.markRegistrationFailed(e.getMessage());
            log.error("❌ Ошибка регистрации TelegramBotService: {}", e.getMessage());

            if (e.getMessage().contains("can't use getUpdates method while webhook is active")) {
                log.error(
                        "💡 РЕШЕНИЕ: Webhook все еще активен! TelegramBotService попытается его удалить автоматически");
                log.error("💡 Если проблема сохраняется, вручно удалите webhook через Bot API");
            }
        }

        // Предупреждаем, если webhook сервис активен в dev режиме
        if (telegramWebhookBotService != null) {
            log.warn("⚠️ TelegramWebhookBotService активен в development режиме!");
            log.warn("💡 Убедитесь, что профиль настроен корректно");
        }
    }

    private void registerProductionBot() {
        log.info("🤖 Production mode: инициализируем TelegramWebhookBotService");

        if (telegramWebhookBotService == null) {
            log.error("❌ TelegramWebhookBotService НЕ НАЙДЕН в production режиме!");
            log.error("🔍 Проверьте настройки профиля 'production'");
            return;
        }

        // Инициализируем webhook бот
        telegramWebhookBotService.init();

        log.info("📌 Webhook bots работают независимо от TelegramBotsApi");
        log.info("🌐 Webhook URL: {}", telegramWebhookBotService.getBotPath());
        log.info("👤 Bot Username: {}", telegramWebhookBotService.getBotUsername());
        log.info("🔄 Bot Status: {}", telegramWebhookBotService.getBotStatus());

        if ("ERROR".equals(telegramWebhookBotService.getBotStatus())) {
            log.error("❌ TelegramWebhookBotService в состоянии ERROR: {}",
                    telegramWebhookBotService.getErrorMessage());
        } else {
            log.info("✅ TelegramWebhookBotService готов к приему webhook запросов");
        }

        // Предупреждаем, если polling сервис активен в prod режиме
        if (telegramBotService != null) {
            log.warn("⚠️ TelegramBotService активен в production режиме!");
            log.warn("💡 В production должен использоваться только webhook");
        }
    }
}