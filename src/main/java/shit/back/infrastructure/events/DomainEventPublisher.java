package shit.back.infrastructure.events;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Публикатор доменных событий
 * Заглушка для обеспечения компиляции
 */
@Component
@Slf4j
public class DomainEventPublisher {

    /**
     * Публикация события после завершения транзакции
     */
    public void publishEventAfterTransaction(Object event) {
        log.info("📡 Публикация доменного события: {}", event.getClass().getSimpleName());
        // TODO: Реализовать реальную публикацию событий
    }

    /**
     * Синхронная публикация события
     */
    public void publishEvent(Object event) {
        log.info("📡 Синхронная публикация события: {}", event.getClass().getSimpleName());
        // TODO: Реализовать синхронную публикацию
    }
}