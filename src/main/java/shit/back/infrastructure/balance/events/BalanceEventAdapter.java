package shit.back.infrastructure.balance.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import shit.back.domain.balance.events.BalanceChangedEvent;
import shit.back.entity.TransactionType;

/**
 * Адаптер между доменными событиями и существующими событиями системы
 * 
 * Обеспечивает:
 * - Преобразование новых доменных событий в существующие события
 * - Backward compatibility с текущими обработчиками событий
 * - Интеграцию между DDD архитектурой и legacy кодом
 * - Постепенный переход на новую событийную модель
 */
@Component
public class BalanceEventAdapter {

    private static final Logger log = LoggerFactory.getLogger(BalanceEventAdapter.class);

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    /**
     * Адаптация доменного события BalanceChangedEvent
     * к существующим событиям системы баланса
     * 
     * @param domainEvent доменное событие изменения баланса
     */
    public void adaptBalanceChangedEvent(BalanceChangedEvent domainEvent) {
        log.debug("Адаптация доменного события BalanceChangedEvent для пользователя {}",
                domainEvent.getUserId());

        try {
            // Определяем тип операции и создаем соответствующее legacy событие
            TransactionType transactionType = domainEvent.getOperationType();

            switch (transactionType) {
                case DEPOSIT -> publishDepositEvent(domainEvent);
                case WITHDRAWAL -> publishWithdrawalEvent(domainEvent);
                case PURCHASE -> publishPurchaseEvent(domainEvent);
                case REFUND -> publishRefundEvent(domainEvent);
                case ADJUSTMENT -> publishAdjustmentEvent(domainEvent);
                default -> {
                    log.warn("Неизвестный тип транзакции для адаптации: {}", transactionType);
                    publishGenericBalanceEvent(domainEvent);
                }
            }

        } catch (Exception e) {
            log.error("Ошибка при адаптации доменного события BalanceChangedEvent: {}",
                    e.getMessage(), e);
            // Не прерываем выполнение, так как это может нарушить основной бизнес-процесс
        }
    }

    /**
     * Публикация события пополнения баланса
     */
    private void publishDepositEvent(BalanceChangedEvent domainEvent) {
        try {
            // Создаем legacy событие используя существующий класс
            shit.back.domain.balance.events.BalanceChangedEvent legacyEvent = new shit.back.domain.balance.events.BalanceChangedEvent(
                    this,
                    domainEvent.getUserId(),
                    domainEvent.getOperationType(),
                    domainEvent.getAmount(),
                    domainEvent.getBalanceBefore(),
                    domainEvent.getBalanceAfter(),
                    domainEvent.getTransactionId(),
                    domainEvent.getDescription());

            applicationEventPublisher.publishEvent(legacyEvent);
            log.debug("Опубликовано legacy событие пополнения для пользователя {}",
                    domainEvent.getUserId());

        } catch (Exception e) {
            log.error("Ошибка публикации legacy события пополнения: {}", e.getMessage(), e);
        }
    }

    /**
     * Публикация события снятия с баланса
     */
    private void publishWithdrawalEvent(BalanceChangedEvent domainEvent) {
        try {
            // Аналогично создаем событие снятия
            shit.back.domain.balance.events.BalanceChangedEvent legacyEvent = new shit.back.domain.balance.events.BalanceChangedEvent(
                    this,
                    domainEvent.getUserId(),
                    domainEvent.getOperationType(),
                    domainEvent.getAmount(),
                    domainEvent.getBalanceBefore(),
                    domainEvent.getBalanceAfter(),
                    domainEvent.getTransactionId(),
                    domainEvent.getDescription());

            applicationEventPublisher.publishEvent(legacyEvent);
            log.debug("Опубликовано legacy событие снятия для пользователя {}",
                    domainEvent.getUserId());

        } catch (Exception e) {
            log.error("Ошибка публикации legacy события снятия: {}", e.getMessage(), e);
        }
    }

    /**
     * Публикация события покупки
     */
    private void publishPurchaseEvent(BalanceChangedEvent domainEvent) {
        try {
            // Создаем событие покупки/резервирования
            if (domainEvent.getOrderId() != null) {
                // Если есть orderId, это резервирование
                // Класс BalanceReservedEvent отсутствует, fallback на generic событие
                publishGenericBalanceEvent(domainEvent);
                log.debug("Опубликовано generic событие покупки/резервирования для заказа {}",
                        domainEvent.getOrderId());
            } else {
                // Обычная покупка
                publishGenericBalanceEvent(domainEvent);
            }

        } catch (Exception e) {
            log.error("Ошибка публикации legacy события покупки: {}", e.getMessage(), e);
        }
    }

    /**
     * Публикация события возврата
     */
    private void publishRefundEvent(BalanceChangedEvent domainEvent) {
        try {
            if (domainEvent.getOrderId() != null) {
                // Если есть orderId, это освобождение резерва
                // Класс BalanceReleasedEvent отсутствует, fallback на generic событие
                publishGenericBalanceEvent(domainEvent);
                log.debug("Опубликовано generic событие возврата/освобождения для заказа {}",
                        domainEvent.getOrderId());
            } else {
                // Обычный refund
                publishGenericBalanceEvent(domainEvent);
            }

        } catch (Exception e) {
            log.error("Ошибка публикации legacy события возврата: {}", e.getMessage(), e);
        }
    }

    /**
     * Публикация события административной корректировки
     */
    private void publishAdjustmentEvent(BalanceChangedEvent domainEvent) {
        try {
            // Административные корректировки публикуем как обычные изменения баланса
            publishGenericBalanceEvent(domainEvent);
            log.debug("Опубликовано legacy событие корректировки для пользователя {}",
                    domainEvent.getUserId());

        } catch (Exception e) {
            log.error("Ошибка публикации legacy события корректировки: {}", e.getMessage(), e);
        }
    }

    /**
     * Публикация общего события изменения баланса
     */
    private void publishGenericBalanceEvent(BalanceChangedEvent domainEvent) {
        try {
            shit.back.domain.balance.events.BalanceChangedEvent legacyEvent = new shit.back.domain.balance.events.BalanceChangedEvent(
                    this,
                    domainEvent.getUserId(),
                    domainEvent.getOperationType(),
                    domainEvent.getAmount(),
                    domainEvent.getBalanceBefore(),
                    domainEvent.getBalanceAfter(),
                    domainEvent.getTransactionId(),
                    domainEvent.getDescription());

            applicationEventPublisher.publishEvent(legacyEvent);
            log.debug("Опубликовано общее legacy событие изменения баланса для пользователя {}",
                    domainEvent.getUserId());

        } catch (Exception e) {
            log.error("Ошибка публикации общего legacy события: {}", e.getMessage(), e);
        }
    }

    /**
     * Адаптация для других типов доменных событий (для будущего расширения)
     */
    public void adaptDomainEvent(Object domainEvent) {
        log.debug("Адаптация доменного события: {}", domainEvent.getClass().getSimpleName());

        if (domainEvent instanceof BalanceChangedEvent) {
            adaptBalanceChangedEvent((BalanceChangedEvent) domainEvent);
        } else {
            log.warn("Неизвестный тип доменного события для адаптации: {}",
                    domainEvent.getClass().getSimpleName());
        }
    }

    /**
     * Проверка совместимости доменного события с legacy системой
     */
    public boolean isCompatibleEvent(Object domainEvent) {
        return domainEvent instanceof BalanceChangedEvent;
    }

    /**
     * Получение статистики адаптированных событий
     */
    public void logAdaptationStatistics() {
        // В реальном приложении здесь можно вести статистику
        // количества адаптированных событий разных типов
        log.info("Статистика адаптации событий доступна через метрики приложения");
    }
}