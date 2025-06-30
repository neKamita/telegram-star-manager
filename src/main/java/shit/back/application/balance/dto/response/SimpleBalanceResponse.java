package shit.back.application.balance.dto.response;

import shit.back.domain.balance.valueobjects.Currency;
import shit.back.domain.balance.valueobjects.Money;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Упрощенный DTO для единого баланса пользователя
 * 
 * Заменяет сложную DualBalanceResponse логику простым подходом с единым
 * балансом.
 * Следует принципам SOLID, DRY, Clean Code, KISS.
 */
public final class SimpleBalanceResponse {

    private final Long userId;
    private final Money currentBalance;
    private final Currency currency;
    private final boolean active;
    private final LocalDateTime lastUpdated;

    /**
     * Приватный конструктор для Builder pattern
     */
    private SimpleBalanceResponse(Builder builder) {
        this.userId = builder.userId;
        this.currentBalance = builder.currentBalance;
        this.currency = builder.currency;
        this.active = builder.active;
        this.lastUpdated = builder.lastUpdated;
    }

    /**
     * Проверка достаточности средств
     * 
     * @param amount требуемая сумма
     * @return true если средств достаточно
     */
    public boolean hasSufficientFunds(Money amount) {
        if (amount == null) {
            return false;
        }
        return active && currentBalance.isGreaterThanOrEqual(amount);
    }

    /**
     * Получение форматированного баланса с символом валюты
     * 
     * @return строка вида "150.50 $"
     */
    public String getFormattedBalance() {
        return String.format("%s %s",
                currentBalance.getFormattedAmount(),
                currency.getSymbol());
    }

    /**
     * Проверка активности баланса
     * 
     * @return true если баланс активен
     */
    public boolean isActive() {
        return active;
    }

    // Геттеры
    public Long getUserId() {
        return userId;
    }

    public Money getCurrentBalance() {
        return currentBalance;
    }

    public Currency getCurrency() {
        return currency;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    /**
     * Получение форматированной даты обновления
     */
    public String getFormattedLastUpdated() {
        if (lastUpdated == null) {
            return "Не обновлялся";
        }
        return lastUpdated.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
    }

    /**
     * Builder для создания SimpleBalanceResponse
     */
    public static class Builder {
        private Long userId;
        private Money currentBalance;
        private Currency currency;
        private boolean active = true;
        private LocalDateTime lastUpdated;

        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder currentBalance(Money currentBalance) {
            this.currentBalance = currentBalance;
            return this;
        }

        public Builder currency(Currency currency) {
            this.currency = currency;
            return this;
        }

        public Builder active(boolean active) {
            this.active = active;
            return this;
        }

        public Builder lastUpdated(LocalDateTime lastUpdated) {
            this.lastUpdated = lastUpdated;
            return this;
        }

        public SimpleBalanceResponse build() {
            if (userId == null) {
                throw new IllegalArgumentException("userId обязателен");
            }
            if (currentBalance == null) {
                throw new IllegalArgumentException("currentBalance обязателен");
            }
            if (currency == null) {
                throw new IllegalArgumentException("currency обязательна");
            }
            if (lastUpdated == null) {
                lastUpdated = LocalDateTime.now();
            }

            return new SimpleBalanceResponse(this);
        }
    }

    /**
     * Factory method для создания билдера
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return String.format("SimpleBalanceResponse{userId=%d, balance=%s, currency=%s, active=%s}",
                userId,
                currentBalance != null ? currentBalance.getFormattedAmount() : "null",
                currency != null ? currency.getCode() : "null",
                active);
    }
}