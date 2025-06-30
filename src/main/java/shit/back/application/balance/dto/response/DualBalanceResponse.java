package shit.back.application.balance.dto.response;

import shit.back.domain.balance.valueobjects.Currency;
import shit.back.domain.balance.valueobjects.Money;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * DTO для ответа с информацией о двойном балансе
 * 
 * Представляет пополненный (bank) и рабочий (main) балансы пользователя
 */
public class DualBalanceResponse {

    private final Long userId;
    private final Money bankBalance; // Пополненный баланс
    private final Money mainBalance; // Рабочий баланс
    private final Currency currency;
    private final boolean active;
    private final LocalDateTime lastUpdated;
    private final Money totalTransferredToMain; // Всего переведено в рабочий
    private final Money totalSpentFromMain; // Всего потрачено из рабочего

    public DualBalanceResponse(Long userId, Money bankBalance, Money mainBalance,
            Currency currency, boolean active, LocalDateTime lastUpdated,
            Money totalTransferredToMain, Money totalSpentFromMain) {
        this.userId = userId;
        this.bankBalance = bankBalance;
        this.mainBalance = mainBalance;
        this.currency = currency;
        this.active = active;
        this.lastUpdated = lastUpdated != null ? lastUpdated : LocalDateTime.now();
        this.totalTransferredToMain = totalTransferredToMain != null ? totalTransferredToMain : Money.zero();
        this.totalSpentFromMain = totalSpentFromMain != null ? totalSpentFromMain : Money.zero();
    }

    // Основные геттеры
    public Long getUserId() {
        return userId;
    }

    public Money getBankBalance() {
        return bankBalance;
    }

    public Money getMainBalance() {
        return mainBalance;
    }

    public Currency getCurrency() {
        return currency;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public Money getTotalTransferredToMain() {
        return totalTransferredToMain;
    }

    public Money getTotalSpentFromMain() {
        return totalSpentFromMain;
    }

    // Вычисляемые свойства
    public Money getTotalBalance() {
        return bankBalance.add(mainBalance);
    }

    public boolean hasBankFunds() {
        return bankBalance.isPositive();
    }

    public boolean hasMainFunds() {
        return mainBalance.isPositive();
    }

    public boolean hasSufficientMainFunds(Money amount) {
        return mainBalance.isGreaterThanOrEqual(amount);
    }

    public boolean hasSufficientBankFunds(Money amount) {
        return bankBalance.isGreaterThanOrEqual(amount);
    }

    public double getMainBalanceUtilizationRatio() {
        if (totalTransferredToMain.isZero()) {
            return 0.0;
        }
        return totalSpentFromMain.getAmount().doubleValue() / totalTransferredToMain.getAmount().doubleValue();
    }

    public String getFormattedTotalBalance() {
        return getTotalBalance().getFormattedAmount();
    }

    // Builder для удобного создания
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long userId;
        private Money bankBalance = Money.zero();
        private Money mainBalance = Money.zero();
        private Currency currency = Currency.defaultCurrency();
        private boolean active = true;
        private LocalDateTime lastUpdated = LocalDateTime.now();
        private Money totalTransferredToMain = Money.zero();
        private Money totalSpentFromMain = Money.zero();

        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder bankBalance(Money bankBalance) {
            this.bankBalance = bankBalance;
            return this;
        }

        public Builder mainBalance(Money mainBalance) {
            this.mainBalance = mainBalance;
            return this;
        }

        public Builder currency(Currency currency) {
            this.currency = currency;
            // Обновляем валюту для всех Money объектов
            if (this.bankBalance != null) {
                this.bankBalance = Money.of(this.bankBalance.getAmount());
            }
            if (this.mainBalance != null) {
                this.mainBalance = Money.of(this.mainBalance.getAmount());
            }
            if (this.totalTransferredToMain != null) {
                this.totalTransferredToMain = Money.of(this.totalTransferredToMain.getAmount());
            }
            if (this.totalSpentFromMain != null) {
                this.totalSpentFromMain = Money.of(this.totalSpentFromMain.getAmount());
            }
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

        public Builder totalTransferredToMain(Money totalTransferredToMain) {
            this.totalTransferredToMain = totalTransferredToMain;
            return this;
        }

        public Builder totalSpentFromMain(Money totalSpentFromMain) {
            this.totalSpentFromMain = totalSpentFromMain;
            return this;
        }

        public DualBalanceResponse build() {
            if (userId == null) {
                throw new IllegalStateException("userId обязателен");
            }
            return new DualBalanceResponse(userId, bankBalance, mainBalance, currency,
                    active, lastUpdated, totalTransferredToMain, totalSpentFromMain);
        }
    }

    @Override
    public String toString() {
        return String.format("DualBalanceResponse{userId=%d, bank=%s, main=%s, total=%s, currency=%s}",
                userId, bankBalance.getFormattedAmount(), mainBalance.getFormattedAmount(),
                getTotalBalance().getFormattedAmount(), currency.getCode());
    }
}