package shit.back.application.balance.mapper;

import shit.back.application.balance.dto.response.BalanceResponse;
import shit.back.application.balance.dto.response.DualBalanceResponse;
import shit.back.domain.balance.valueobjects.Currency;
import shit.back.domain.balance.valueobjects.Money;
import shit.back.domain.dualBalance.DualBalanceAggregate;

/**
 * Маппер для преобразования BalanceResponse в DualBalanceResponse
 * 
 * СОЗДАН для решения проблемы несовместимости типов между
 * BalanceApplicationServiceV2.getBalance() и ShowBalanceQueryHandler
 */
public final class BalanceResponseMapper {

    private BalanceResponseMapper() {
        // Utility class
    }

    /**
     * Преобразует BalanceResponse в DualBalanceResponse
     * 
     * @param balanceResponse источник данных
     * @return DualBalanceResponse для использования в Telegram UI
     */
    public static DualBalanceResponse toDualBalanceResponse(BalanceResponse balanceResponse) {
        if (balanceResponse == null) {
            return null;
        }

        // Получаем валюту, используя код из ответа или валюту по умолчанию
        Currency currency = Currency.defaultCurrency();
        if (balanceResponse.getCurrency() != null) {
            currency = Currency.of(balanceResponse.getCurrency());
        }

        // Создаем Money объекты для балансов
        Money currentBalance = Money.of(balanceResponse.getCurrentBalance() != null
                ? balanceResponse.getCurrentBalance()
                : java.math.BigDecimal.ZERO);

        Money totalDeposited = Money.of(balanceResponse.getTotalDeposited() != null
                ? balanceResponse.getTotalDeposited()
                : java.math.BigDecimal.ZERO);

        Money totalSpent = Money.of(balanceResponse.getTotalSpent() != null
                ? balanceResponse.getTotalSpent()
                : java.math.BigDecimal.ZERO);

        // Для простоты считаем весь текущий баланс как "банковский" (пополненный)
        // В будущем можно добавить логику разделения на bank/main балансы
        return DualBalanceResponse.builder()
                .userId(balanceResponse.getUserId())
                .bankBalance(currentBalance) // Весь баланс считаем пополненным
                .mainBalance(Money.zero()) // Рабочий баланс пока нулевой
                .currency(currency)
                .active(balanceResponse.isActive())
                .lastUpdated(balanceResponse.getLastUpdated() != null
                        ? balanceResponse.getLastUpdated()
                        : java.time.LocalDateTime.now())
                .totalTransferredToMain(Money.zero()) // Пока нет переводов
                .totalSpentFromMain(Money.zero()) // Пока нет трат из рабочего
                .build();
    }

    /**
     * Создает заглушку DualBalanceResponse для случаев, когда баланс не найден
     *
     * @param userId ID пользователя
     * @return DualBalanceResponse с нулевыми балансами
     */
    public static DualBalanceResponse createEmptyDualBalance(Long userId) {
        return DualBalanceResponse.builder()
                .userId(userId)
                .bankBalance(Money.zero())
                .mainBalance(Money.zero())
                .currency(Currency.defaultCurrency())
                .active(false)
                .lastUpdated(java.time.LocalDateTime.now())
                .totalTransferredToMain(Money.zero())
                .totalSpentFromMain(Money.zero())
                .build();
    }

    /**
     * Преобразует DualBalanceAggregate в DualBalanceResponse
     *
     * @param dualBalance агрегат двойного баланса
     * @return DualBalanceResponse для использования в UI
     */
    public static DualBalanceResponse fromDualBalance(DualBalanceAggregate dualBalance) {
        if (dualBalance == null) {
            return null;
        }

        return DualBalanceResponse.builder()
                .userId(dualBalance.getUserId())
                .bankBalance(dualBalance.getBankBalance())
                .mainBalance(dualBalance.getMainBalance())
                .currency(dualBalance.getCurrency())
                .active(dualBalance.isActive())
                .lastUpdated(dualBalance.getLastUpdated())
                .totalTransferredToMain(dualBalance.getTotalTransferredToMain())
                .totalSpentFromMain(dualBalance.getTotalSpentFromMain())
                .build();
    }
}