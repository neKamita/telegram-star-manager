package shit.back.application.balance.mapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shit.back.application.balance.dto.response.BalanceResponse;
import shit.back.application.balance.dto.response.SimpleBalanceResponse;
import shit.back.domain.balance.valueobjects.Currency;
import shit.back.domain.balance.valueobjects.Money;
import shit.back.domain.balance.BalanceAggregate;

/**
 * Маппер для преобразования BalanceResponse в SimpleBalanceResponse
 *
 * СОЗДАН для решения проблемы несовместимости типов между
 * BalanceApplicationServiceV2.getBalance() и ShowBalanceQueryHandler
 */
public final class BalanceResponseMapper {

        private static final Logger log = LoggerFactory.getLogger(BalanceResponseMapper.class);

        private BalanceResponseMapper() {
                // Utility class
        }

        /**
         * Преобразует BalanceResponse в SimpleBalanceResponse
         * ИСПРАВЛЕНО: Упрощенная архитектура с единым балансом
         *
         * @param balanceResponse источник данных
         * @return SimpleBalanceResponse для использования в Telegram UI
         */
        public static SimpleBalanceResponse toSimpleBalanceResponse(BalanceResponse balanceResponse) {
                if (balanceResponse == null) {
                        log.warn("🔍 ДИАГНОСТИКА: BalanceResponseMapper получил null balanceResponse");
                        return null;
                }

                // ДИАГНОСТИЧЕСКИЙ ЛОГ: Входные данные
                log.debug(
                                "🔍 ДИАГНОСТИКА BalanceResponseMapper: userId={}, currentBalance={}, totalDeposited={}, totalSpent={}, currency={}",
                                balanceResponse.getUserId(),
                                balanceResponse.getCurrentBalance(),
                                balanceResponse.getTotalDeposited(),
                                balanceResponse.getTotalSpent(),
                                balanceResponse.getCurrency());

                // Получаем валюту, используя код из ответа или валюту по умолчанию
                Currency currency = Currency.defaultCurrency();
                if (balanceResponse.getCurrency() != null) {
                        currency = Currency.of(balanceResponse.getCurrency());
                }

                // Создаем Money объект для текущего баланса
                Money currentBalance = Money.of(balanceResponse.getCurrentBalance() != null
                                ? balanceResponse.getCurrentBalance()
                                : java.math.BigDecimal.ZERO);

                // ДИАГНОСТИЧЕСКИЙ ЛОГ: Результат маппинга
                log.debug(
                                "🔍 ДИАГНОСТИКА BalanceResponseMapper РЕЗУЛЬТАТ: userId={}, currentBalance={} - упрощенная архитектура",
                                balanceResponse.getUserId(),
                                currentBalance.getFormattedAmount());

                return SimpleBalanceResponse.builder()
                                .userId(balanceResponse.getUserId())
                                .currentBalance(currentBalance)
                                .currency(currency)
                                .active(balanceResponse.isActive())
                                .lastUpdated(balanceResponse.getLastUpdated() != null
                                                ? balanceResponse.getLastUpdated()
                                                : java.time.LocalDateTime.now())
                                .build();
        }

        /**
         * Создает заглушку SimpleBalanceResponse для случаев, когда баланс не найден
         *
         * @param userId ID пользователя
         * @return SimpleBalanceResponse с нулевым балансом
         */
        public static SimpleBalanceResponse createEmptyBalance(Long userId) {
                return SimpleBalanceResponse.builder()
                                .userId(userId)
                                .currentBalance(Money.zero())
                                .currency(Currency.defaultCurrency())
                                .active(false)
                                .lastUpdated(java.time.LocalDateTime.now())
                                .build();
        }

        /**
         * Преобразует BalanceAggregate в SimpleBalanceResponse
         *
         * @param balanceAggregate агрегат баланса
         * @return SimpleBalanceResponse для использования в упрощенных стратегиях
         */
        public static SimpleBalanceResponse fromBalanceAggregate(BalanceAggregate balanceAggregate) {
                if (balanceAggregate == null) {
                        log.warn("🔍 ФАЗА2: BalanceResponseMapper получил null balanceAggregate для SimpleBalance");
                        return null;
                }

                log.debug("🔍 ФАЗА2: Преобразование BalanceAggregate в SimpleBalanceResponse - userId={}, currentBalance={}",
                                balanceAggregate.getUserId(),
                                balanceAggregate.getCurrentBalance().getFormattedAmount());

                return SimpleBalanceResponse.builder()
                                .userId(balanceAggregate.getUserId())
                                .currentBalance(balanceAggregate.getCurrentBalance())
                                .currency(balanceAggregate.getCurrency())
                                .active(balanceAggregate.isActive())
                                .lastUpdated(balanceAggregate.getLastUpdated())
                                .build();
        }
}