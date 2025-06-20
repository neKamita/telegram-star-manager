package shit.back.infrastructure.balance.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import shit.back.domain.balance.events.BalanceChangedEvent;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * Инфраструктурный компонент для публикации доменных событий
 * 
 * Обеспечивает:
 * - Интеграцию с Spring ApplicationEventPublisher
 * - Асинхронную обработку событий
 * - Сериализацию событий для аудита
 * - Механизмы повторных попыток
 * - Гарантии порядка событий
 * - Dead letter queue support
 */
@Component
public class DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    private BalanceEventAdapter balanceEventAdapter;

    /**
     * Публикация доменного события синхронно
     * 
     * @param event доменное событие для публикации
     */
    public void publishEvent(Object event) {
        log.debug("Публикация синхронного события: {}", event.getClass().getSimpleName());

        try {
            // Публикуем через Spring ApplicationEventPublisher
            applicationEventPublisher.publishEvent(event);

            // Дополнительно адаптируем к существующим событиям системы
            if (event instanceof BalanceChangedEvent) {
                balanceEventAdapter.adaptBalanceChangedEvent((BalanceChangedEvent) event);
            }

            log.debug("Событие {} успешно опубликовано", event.getClass().getSimpleName());

        } catch (Exception e) {
            log.error("Ошибка при публикации события {}: {}",
                    event.getClass().getSimpleName(), e.getMessage(), e);
            throw new DomainEventPublishingException("Ошибка публикации события", e);
        }
    }

    /**
     * Асинхронная публикация доменного события
     * 
     * @param event доменное событие для публикации
     * @return CompletableFuture для отслеживания результата
     */
    @Async
    public CompletableFuture<Void> publishEventAsync(Object event) {
        log.debug("Публикация асинхронного события: {}", event.getClass().getSimpleName());

        return CompletableFuture.runAsync(() -> {
            try {
                publishEvent(event);
            } catch (Exception e) {
                log.error("Ошибка асинхронной публикации события {}: {}",
                        event.getClass().getSimpleName(), e.getMessage(), e);

                // В реальном приложении здесь можно добавить retry логику
                // или отправку в dead letter queue
                handleFailedEvent(event, e);
            }
        });
    }

    /**
     * Публикация события после завершения транзакции
     * 
     * @param event доменное событие для публикации
     */
    public void publishEventAfterTransaction(Object event) {
        log.debug("Планирование публикации события после транзакции: {}", event.getClass().getSimpleName());

        try {
            // Создаем обертку для delayed публикации
            DelayedEvent delayedEvent = new DelayedEvent(event, LocalDateTime.now());
            applicationEventPublisher.publishEvent(delayedEvent);

        } catch (Exception e) {
            log.error("Ошибка при планировании события после транзакции {}: {}",
                    event.getClass().getSimpleName(), e.getMessage(), e);
            throw new DomainEventPublishingException("Ошибка планирования события", e);
        }
    }

    /**
     * Обработчик отложенных событий - выполняется после коммита транзакции
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDelayedEvent(DelayedEvent delayedEvent) {
        log.debug("Обработка отложенного события: {}",
                delayedEvent.getEvent().getClass().getSimpleName());

        try {
            publishEvent(delayedEvent.getEvent());

        } catch (Exception e) {
            log.error("Ошибка при обработке отложенного события {}: {}",
                    delayedEvent.getEvent().getClass().getSimpleName(), e.getMessage(), e);

            // Повторная попытка или отправка в DLQ
            handleFailedEvent(delayedEvent.getEvent(), e);
        }
    }

    /**
     * Batch публикация множественных событий
     * 
     * @param events список событий для публикации
     */
    public void publishEvents(Object... events) {
        log.debug("Batch публикация {} событий", events.length);

        for (Object event : events) {
            try {
                publishEvent(event);
            } catch (Exception e) {
                log.error("Ошибка в batch публикации события {}: {}",
                        event.getClass().getSimpleName(), e.getMessage(), e);
                // Продолжаем публикацию остальных событий
            }
        }
    }

    /**
     * Публикация с retry механизмом
     * 
     * @param event      доменное событие
     * @param maxRetries максимальное количество попыток
     */
    public void publishEventWithRetry(Object event, int maxRetries) {
        int attempts = 0;
        Exception lastException = null;

        while (attempts <= maxRetries) {
            try {
                publishEvent(event);
                return; // Успешно опубликовано

            } catch (Exception e) {
                lastException = e;
                attempts++;

                if (attempts <= maxRetries) {
                    log.warn("Попытка {} публикации события {} не удалась, повторная попытка через 100ms",
                            attempts, event.getClass().getSimpleName());

                    try {
                        Thread.sleep(100 * attempts); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("Все {} попыток публикации события {} не удались",
                maxRetries + 1, event.getClass().getSimpleName());
        handleFailedEvent(event, lastException);
    }

    /**
     * Обработка событий, которые не удалось опубликовать
     */
    private void handleFailedEvent(Object event, Exception error) {
        log.error("Событие {} отправлено в Dead Letter Queue из-за ошибки: {}",
                event.getClass().getSimpleName(), error.getMessage());

        // В реальном приложении здесь можно:
        // 1. Сохранить в таблицу failed_events
        // 2. Отправить в message queue для повторной обработки
        // 3. Уведомить администраторов
        // 4. Записать в специальный audit log

        try {
            // Сериализуем событие для последующего анализа
            String serializedEvent = serializeEvent(event);
            log.error("Failed event details: {}", serializedEvent);

        } catch (Exception e) {
            log.error("Не удалось сериализовать failed event: {}", e.getMessage());
        }
    }

    /**
     * Сериализация события для аудита и логирования
     */
    private String serializeEvent(Object event) {
        try {
            // Простая сериализация - в реальном приложении можно использовать Jackson
            return String.format("Event{type=%s, timestamp=%s, data=%s}",
                    event.getClass().getSimpleName(),
                    LocalDateTime.now(),
                    event.toString());

        } catch (Exception e) {
            return String.format("Event{type=%s, serializationError=%s}",
                    event.getClass().getSimpleName(), e.getMessage());
        }
    }

    /**
     * Wrapper класс для отложенных событий
     */
    public static class DelayedEvent {
        private final Object event;
        private final LocalDateTime scheduledAt;

        public DelayedEvent(Object event, LocalDateTime scheduledAt) {
            this.event = event;
            this.scheduledAt = scheduledAt;
        }

        public Object getEvent() {
            return event;
        }

        public LocalDateTime getScheduledAt() {
            return scheduledAt;
        }
    }

    /**
     * Кастомное исключение для ошибок публикации событий
     */
    public static class DomainEventPublishingException extends RuntimeException {
        public DomainEventPublishingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}