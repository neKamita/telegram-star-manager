package shit.back.telegram.ui.strategy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import shit.back.application.balance.dto.response.DualBalanceResponse;
import shit.back.config.PaymentConfigurationProperties;
import shit.back.domain.balance.valueobjects.Currency;
import shit.back.telegram.ui.strategy.utils.PaymentMethodsHelper;

import java.util.List;

/**
 * Стратегия приветственной карточки
 *
 * Отображает упрощенное приветствие с общим балансом и доступными способами
 * пополнения.
 * Техническая детализация dual balance скрыта для удобства пользователя.
 */
@Component
public class WelcomeCardStrategy implements TelegramMessageStrategy {

    private static final String STRATEGY_TYPE = "WELCOME_CARD";
    private static final String[] SUPPORTED_TYPES = {
            "USER_WELCOME_CARD", "PAYMENT_METHODS_CARD", "ONBOARDING_CARD"
    };

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
            case "USER_WELCOME_CARD" -> formatUserWelcomeCard(data);
            case "PAYMENT_METHODS_CARD" -> formatPaymentMethodsCard(data);
            case "ONBOARDING_CARD" -> formatOnboardingCard(data);
            default -> throw new IllegalArgumentException("Неподдерживаемый тип контента: " + contentType);
        };
    }

    /**
     * Приветственная карточка пользователя с балансом
     */
    private String formatUserWelcomeCard(Object data) {
        WelcomeCardData cardData = parseWelcomeCardData(data);

        StringBuilder message = new StringBuilder();
        message.append(String.format("👋 <b>Добро пожаловать, %s!</b>\n\n", cardData.userName));

        // Информация о балансе если есть
        if (cardData.balance != null) {
            String currencySymbol = cardData.balance.getCurrency().getSymbol();
            boolean hasBalance = cardData.balance.getTotalBalance().isPositive();

            if (hasBalance) {
                message.append(String.format("💰 <b>Ваш баланс:</b> %s %s\n\n",
                        cardData.balance.getTotalBalance().getFormattedAmount(), currencySymbol));
                message.append("✅ <b>Готово к покупке звезд!</b>\n\n");
            } else {
                message.append("💰 <b>Ваш баланс:</b> 0,00 ").append(currencySymbol).append("\n\n");
                message.append("🚀 <i>Пополните баланс для покупки звезд!</i>\n\n");
            }
        }

        // Объяснение как работает система
        message.append("⭐ <b>Как покупать звезды:</b>\n");
        message.append("1️⃣ Пополните баланс удобным способом\n");
        message.append("2️⃣ Бот автоматически покупает звезды ЗА вас\n");
        message.append("3️⃣ Звезды зачисляются на ваш Telegram аккаунт\n\n");

        // Доступные способы пополнения
        Currency userCurrency = cardData.balance != null ? cardData.balance.getCurrency() : Currency.defaultCurrency();
        List<String> paymentMethods = PaymentMethodsHelper.getAvailablePaymentMethods(userCurrency, paymentConfig);

        if (!paymentMethods.isEmpty()) {
            message.append("💳 <b>Способы пополнения:</b>\n");
            for (String method : paymentMethods) {
                message.append(String.format("• %s\n", method));
            }
        } else {
            message.append("⚠️ <i>Настройте способы оплаты в конфигурации</i>");
        }

        return message.toString();
    }

    /**
     * Карточка с доступными способами оплаты
     */
    private String formatPaymentMethodsCard(Object data) {
        Currency currency = data instanceof Currency ? (Currency) data : Currency.defaultCurrency();

        StringBuilder message = new StringBuilder();
        message.append("💳 <b>Способы пополнения баланса</b>\n\n");

        List<PaymentMethodsHelper.PaymentMethodInfo> methods = PaymentMethodsHelper.getDetailedPaymentMethods(currency,
                paymentConfig);

        if (methods.isEmpty()) {
            message.append("⚠️ <i>Нет доступных способов оплаты для валюты </i>");
            message.append(currency.getFormattedName()).append("\n\n");
            message.append("💡 Обратитесь к администратору для настройки способов оплаты");
            return message.toString();
        }

        for (PaymentMethodsHelper.PaymentMethodInfo method : methods) {
            message.append(String.format("• %s\n", method.getDisplayName()));
            message.append(String.format("  💱 <i>%s</i>\n", method.getDescription()));
            if (method.hasLimits()) {
                message.append(String.format("  📊 <i>%s</i>\n", method.getLimitsText()));
            }
            message.append("\n");
        }

        message.append("🤖 <b>Важно:</b> Бот покупает звезды через Telegram Fragment,\n");
        message.append("используя корпоративный баланс после вашего пополнения");

        return message.toString();
    }

    /**
     * Обучающая карточка для новых пользователей
     */
    private String formatOnboardingCard(Object data) {
        String userName = data instanceof String ? (String) data : "пользователь";

        return String.format("""
                🎯 <b>Добро пожаловать в Star Manager, %s!</b>

                ⭐ <b>Что это за бот?</b>
                Удобный способ покупки Telegram Stars без сложностей с оплатой

                🔄 <b>Как это работает?</b>
                • Вы пополняете баланс привычным способом
                • Мы покупаем звезды ЗА вас через Telegram
                • Звезды приходят на ваш аккаунт

                💡 <b>Преимущества:</b>
                • Не нужно вводить карту каждый раз
                • Безопасные платежи через проверенные системы
                • Автоматическая покупка звезд
                • История всех операций

                🚀 <b>Начните с пополнения баланса!</b>

                ❓ Используйте /help для получения помощи
                """, userName);
    }

    // Методы перенесены в PaymentMethodsHelper

    /**
     * Парсинг данных приветственной карточки
     */
    private WelcomeCardData parseWelcomeCardData(Object data) {
        if (data instanceof WelcomeCardData) {
            return (WelcomeCardData) data;
        }
        if (data instanceof String) {
            return new WelcomeCardData((String) data, null);
        }
        throw new IllegalArgumentException("Неподдерживаемый тип данных для USER_WELCOME_CARD");
    }

    /**
     * Данные для приветственной карточки
     */
    public static class WelcomeCardData {
        public final String userName;
        public final DualBalanceResponse balance;

        public WelcomeCardData(String userName, DualBalanceResponse balance) {
            this.userName = userName != null ? userName : "пользователь";
            this.balance = balance;
        }
    }

    // PaymentMethodInfo перенесена в PaymentMethodsHelper
}