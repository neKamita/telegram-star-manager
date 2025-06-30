package shit.back.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

/**
 * Конфигурация для новой функциональной архитектуры Telegram
 * 
 * Заменяет старую TelegramApplicationConfiguration
 * и обеспечивает более простую настройку
 */
@Configuration
@Slf4j
public class TelegramConfiguration {

    /**
     * Логирование успешной инициализации после загрузки контекста
     */
    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationReady() {
        log.info("🚀 Новая функциональная архитектура Telegram успешно инициализирована");
        log.info("📋 TelegramService автоматически зарегистрировал все доступные обработчики");
        log.info("✅ Система готова к обработке Telegram запросов");
    }

    /**
     * В будущем здесь можно добавить дополнительные Bean'ы для:
     * - UI Factory компонентов
     * - Cache менеджеров для запросов
     * - Metrics коллекторов
     * - Feature toggles
     */
}