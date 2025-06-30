package shit.back.telegram.ui.strategy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import shit.back.application.balance.dto.response.SimpleBalanceResponse;
import shit.back.config.PaymentConfigurationProperties;
import shit.back.domain.balance.valueobjects.Currency;
import shit.back.telegram.ui.strategy.utils.PaymentMethodsHelper;
import shit.back.telegram.ui.strategy.utils.StrategyConstants;

import java.util.List;

/**
 * Стратегия отображения баланса пользователя
 *
 * Показывает упрощенную информацию о балансе - только общий доступный баланс.
 * Техническая детализация на Bank/Main балансы скрыта от пользователя для
 * простоты.
 */
@Component
public class BalanceDisplayStrategy implements TelegramMessageStrategy {

    private static final String STRATEGY_TYPE = "BALANCE_DISPLAY";
    private static final String[] SUPPORTED_TYPES = {
            "DUAL_BALANCE_INFO", "BALANCE_SUMMARY", "BALANCE_DETAILS"
    };

    // Используем константы из StrategyConstants

    @Autowired
    private PaymentConfigurationProperties paymentConfig;

    @Override
    public String getStrategyType() {
        return STRATEGY_TYPE;
    }

    @Override
    public boolean canHandle(String contentType) {
        if (contentType == null)
            return false;
        for (String type : SUPPORTED_TYPES) {
            if (type.equals(contentType))
                return true;
        }
        return false;
    }

    @Override
    public String[] getSupportedContentTypes() {
        return SUPPORTED_TYPES.clone();
    }

    @Override
    public String formatContent(String contentType, Object data) {
        return switch (contentType) {
            case "DUAL_BALANCE_INFO" -> formatDualBalanceInfo(data);
            case "BALANCE_SUMMARY" -> formatBalanceSummary(data);
            case "BALANCE_DETAILS" -> formatBalanceDetails(data);
            default -> throw new IllegalArgumentException("Неподдерживаемый тип контента: " + contentType);
        };
    }

    /**
     * Форматирование упрощенной информации о балансе
     * Показывает только общий доступный баланс без технических деталей
     */
    private String formatDualBalanceInfo(Object data) {
        if (!(data instanceof SimpleBalanceResponse balance)) {
            throw new IllegalArgumentException("Ожидался SimpleBalanceResponse для DUAL_BALANCE_INFO");
        }

        String statusIcon = balance.isActive() ? "✅" : "❌";
        String currencySymbol = balance.getCurrency().getSymbol();

        StringBuilder message = new StringBuilder();
        message.append(String.format("💰 <b>Ваш баланс:</b> %s\n\n",
                balance.getFormattedBalance()));

        // Информация о валюте и статусе
        message.append(String.format("💱 <b>Валюта:</b> %s\n", balance.getCurrency().getFormattedName()));
        message.append(String.format("%s <b>Статус:</b> %s\n", statusIcon,
                balance.isActive() ? "Активен" : "Неактивен"));
        message.append(String.format("🕐 <b>Обновлен:</b> %s",
                balance.getLastUpdated().format(StrategyConstants.DATE_FORMATTER)));

        return message.toString();
    }

    /**
     * Форматирование краткой сводки баланса
     */
    private String formatBalanceSummary(Object data) {
        if (!(data instanceof SimpleBalanceResponse balance)) {
            throw new IllegalArgumentException("Ожидался SimpleBalanceResponse для BALANCE_SUMMARY");
        }

        String currencySymbol = balance.getCurrency().getSymbol();
        boolean hasAvailableFunds = balance.getCurrentBalance().isPositive();
        String fundsIcon = hasAvailableFunds ? "💚" : "⚠️";

        return String.format("""
                %s <b>Баланс:</b> %s

                %s <i>Статус: %s</i>
                """,
                fundsIcon, balance.getFormattedBalance(),
                balance.isActive() ? "✅" : "❌",
                hasAvailableFunds ? "Готов к покупкам" : "Пополните баланс");
    }

    /**
     * Форматирование детальной информации с рекомендациями
     */
    private String formatBalanceDetails(Object data) {
        if (!(data instanceof SimpleBalanceResponse balance)) {
            throw new IllegalArgumentException("Ожидался SimpleBalanceResponse для BALANCE_DETAILS");
        }

        StringBuilder message = new StringBuilder();
        String currencySymbol = balance.getCurrency().getSymbol();
        boolean hasAvailableFunds = balance.getCurrentBalance().isPositive();

        message.append("📋 <b>Детали баланса</b>\n\n");

        // Общий баланс
        message.append(String.format("💰 <b>Доступно:</b> %s\n\n",
                balance.getFormattedBalance()));

        if (hasAvailableFunds) {
            message.append("⭐ <i>Готов для покупки звезд</i>\n\n");
        } else {
            message.append("⚠️ <i>Пополните баланс для покупки звезд</i>\n\n");
        }

        // Рекомендации по пополнению
        message.append("💡 <b>Доступные способы пополнения:</b>\n");
        List<String> availableMethods = PaymentMethodsHelper.getAvailablePaymentMethods(balance.getCurrency(),
                paymentConfig);
        for (String method : availableMethods) {
            message.append(String.format("• %s\n", method));
        }

        if (availableMethods.isEmpty()) {
            message.append("⚠️ <i>Нет доступных способов для данной валюты</i>");
        }

        return message.toString();
    }
}