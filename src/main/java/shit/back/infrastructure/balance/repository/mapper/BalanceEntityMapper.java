package shit.back.infrastructure.balance.repository.mapper;

import org.springframework.stereotype.Component;
import shit.back.domain.balance.BalanceAggregate;
import shit.back.domain.balance.valueobjects.BalanceId;
import shit.back.domain.balance.valueobjects.Currency;
import shit.back.domain.balance.valueobjects.Money;
import shit.back.entity.UserBalanceEntity;

import java.time.LocalDateTime;

/**
 * Маппер для преобразования между BalanceAggregate (Domain) и UserBalanceEntity
 * (JPA)
 * 
 * Обеспечивает:
 * - Преобразование Domain Objects ↔ JPA Entities
 * - Корректное маппинг денежных сумм и валют
 * - Обработка временных меток и аудита
 * - Version control маппинг
 */
@Component
public class BalanceEntityMapper {

    /**
     * Преобразование JPA Entity в Domain Aggregate
     */
    public BalanceAggregate entityToAggregate(UserBalanceEntity entity) {
        if (entity == null) {
            return null;
        }

        BalanceId balanceId = entity.getId() != null ? new BalanceId(entity.getId()) : null;
        Currency currency = Currency.fromCode(entity.getCurrency());

        Money currentBalance = new Money(entity.getCurrentBalance(), currency);
        Money totalDeposited = new Money(entity.getTotalDeposited(), currency);
        Money totalSpent = new Money(entity.getTotalSpent(), currency);

        BalanceAggregate aggregate = new BalanceAggregate(
                balanceId,
                entity.getUserId(),
                currency,
                currentBalance,
                totalDeposited,
                totalSpent,
                entity.getIsActive(),
                entity.getCreatedAt(),
                entity.getLastUpdated());

        // Установка дополнительных полей
        if (entity.getNotes() != null) {
            aggregate.setNotes(entity.getNotes());
        }

        return aggregate;
    }

    /**
     * Преобразование Domain Aggregate в JPA Entity (для создания новой записи)
     */
    public UserBalanceEntity aggregateToEntity(BalanceAggregate aggregate) {
        if (aggregate == null) {
            return null;
        }

        UserBalanceEntity entity = new UserBalanceEntity();

        // ID устанавливается только если есть
        if (aggregate.getId() != null) {
            // Корректно получаем значение идентификатора BalanceId
            if (aggregate.getId() instanceof Long) {
                entity.setId((Long) aggregate.getId());
            } else {
                entity.setId(null);
            }
        }

        entity.setUserId(aggregate.getUserId());
        entity.setCurrency(aggregate.getCurrency().getCode());
        entity.setCurrentBalance(aggregate.getCurrentBalance().getAmount());
        entity.setTotalDeposited(aggregate.getTotalDeposited().getAmount());
        entity.setTotalSpent(aggregate.getTotalSpent().getAmount());
        entity.setIsActive(aggregate.isActive());

        // Временные метки
        if (aggregate.getCreatedAt() != null) {
            entity.setCreatedAt(aggregate.getCreatedAt());
        }
        if (aggregate.getLastUpdated() != null) {
            entity.setLastUpdated(aggregate.getLastUpdated());
        }

        // Дополнительные поля
        if (aggregate.getNotes() != null) {
            entity.setNotes(aggregate.getNotes());
        }

        return entity;
    }

    /**
     * Обновление существующей JPA Entity данными из Domain Aggregate
     */
    public void updateEntityFromAggregate(UserBalanceEntity entity, BalanceAggregate aggregate) {
        if (entity == null || aggregate == null) {
            return;
        }

        // Обновляем только изменяемые поля
        entity.setCurrentBalance(aggregate.getCurrentBalance().getAmount());
        entity.setTotalDeposited(aggregate.getTotalDeposited().getAmount());
        entity.setTotalSpent(aggregate.getTotalSpent().getAmount());
        entity.setIsActive(aggregate.isActive());

        // Валюта может измениться в редких случаях
        entity.setCurrency(aggregate.getCurrency().getCode());

        // Обновляем временную метку
        entity.setLastUpdated(LocalDateTime.now());

        // Дополнительные поля
        if (aggregate.getNotes() != null) {
            entity.setNotes(aggregate.getNotes());
        }
    }

    /**
     * Создание нового BalanceAggregate для указанного пользователя
     */
    public BalanceAggregate createNewBalance(Long userId, Currency currency) {
        Money zero = new Money(java.math.BigDecimal.ZERO, currency);

        return new BalanceAggregate(
                null, // ID будет установлен после сохранения
                userId,
                currency,
                zero, // currentBalance
                zero, // totalDeposited
                zero, // totalSpent
                true, // isActive
                LocalDateTime.now(), // createdAt
                LocalDateTime.now() // lastUpdated
        );
    }

    /**
     * Проверка соответствия Entity и Aggregate
     */
    public boolean isEquivalent(UserBalanceEntity entity, BalanceAggregate aggregate) {
        if (entity == null && aggregate == null) {
            return true;
        }
        if (entity == null || aggregate == null) {
            return false;
        }

        return entity.getUserId().equals(aggregate.getUserId()) &&
                entity.getCurrency().equals(aggregate.getCurrency().getCode()) &&
                entity.getCurrentBalance().compareTo(aggregate.getCurrentBalance().getAmount()) == 0 &&
                entity.getTotalDeposited().compareTo(aggregate.getTotalDeposited().getAmount()) == 0 &&
                entity.getTotalSpent().compareTo(aggregate.getTotalSpent().getAmount()) == 0 &&
                entity.getIsActive().equals(aggregate.isActive());
    }
}