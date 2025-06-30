package shit.back.telegram.ui.strategy.utils;

import shit.back.config.PaymentConfigurationProperties;
import shit.back.domain.balance.valueobjects.Currency;

import java.util.ArrayList;
import java.util.List;

/**
 * Утилитарный класс для централизованной работы со способами оплаты
 * 
 * Устраняет дублирование логики способов оплаты в различных стратегиях
 */
public final class PaymentMethodsHelper {

    private PaymentMethodsHelper() {
        // Утилитарный класс - запрещаем создание экземпляров
    }

    /**
     * Получение доступных способов оплаты для валюты (простой формат)
     */
    public static List<String> getAvailablePaymentMethods(Currency currency, PaymentConfigurationProperties config) {
        List<String> methods = new ArrayList<>();

        // TON Wallet
        if (config.getTon().getEnabled() && isTonCompatible(currency)) {
            methods.add(getPaymentMethodDisplayName("TON"));
        }

        // YooKassa (только USD)
        if (config.getYookassa().getEnabled() && currency.isUsd()) {
            methods.add(getPaymentMethodDisplayName("YOOKASSA"));
        }

        // UZS Payment (только UZS)
        if (config.getUzsPayment().getEnabled() && currency.isUzs()) {
            methods.add(getPaymentMethodDisplayName("UZS_PAYMENT"));
        }

        return methods;
    }

    /**
     * Получение детальной информации о способах оплаты
     */
    public static List<PaymentMethodInfo> getDetailedPaymentMethods(Currency currency,
            PaymentConfigurationProperties config) {
        List<PaymentMethodInfo> methods = new ArrayList<>();

        // TON Wallet
        if (config.getTon().getEnabled() && isTonCompatible(currency)) {
            methods.add(new PaymentMethodInfo(
                    getPaymentMethodDisplayName("TON"),
                    "Оплата криптовалютой TON",
                    "Комиссия сети: " + config.getTon().getNetworkFeePercent() + "%"));
        }

        // YooKassa
        if (config.getYookassa().getEnabled() && currency.isUsd()) {
            methods.add(new PaymentMethodInfo(
                    getPaymentMethodDisplayName("YOOKASSA"),
                    "Банковские карты (Visa, MasterCard, МИР)",
                    "Поддержка международных карт"));
        }

        // UZS Payment
        if (config.getUzsPayment().getEnabled() && currency.isUzs()) {
            String limitsText = String.format("Лимиты: %,d - %,d UZS",
                    config.getUzsPayment().getMinAmountUzs(),
                    config.getUzsPayment().getMaxAmountUzs());
            methods.add(new PaymentMethodInfo(
                    getPaymentMethodDisplayName("UZS_PAYMENT"),
                    "Локальные карты Узбекистана",
                    limitsText));
        }

        return methods;
    }

    /**
     * Получение иконки способа оплаты
     */
    public static String getPaymentMethodIcon(String method) {
        return switch (method.toUpperCase()) {
            case "TON" -> "🪙";
            case "YOOKASSA" -> "💳";
            case "UZS_PAYMENT" -> "🏛️";
            default -> "💰";
        };
    }

    /**
     * Получение отображаемого названия способа оплаты
     */
    public static String getPaymentMethodDisplayName(String method) {
        return switch (method.toUpperCase()) {
            case "TON" -> "🪙 TON Wallet";
            case "YOOKASSA" -> "💳 YooKassa (Банковские карты)";
            case "UZS_PAYMENT" -> "🏛️ UZS Payment (UzCard, Humo, Visa, MasterCard)";
            default -> "💰 " + method;
        };
    }

    /**
     * Проверка совместимости валюты с TON
     */
    private static boolean isTonCompatible(Currency currency) {
        return currency.isUsd() || currency.toString().equals("TON");
    }

    /**
     * Информация о способе оплаты
     */
    public static class PaymentMethodInfo {
        private final String displayName;
        private final String description;
        private final String limitsText;

        public PaymentMethodInfo(String displayName, String description, String limitsText) {
            this.displayName = displayName;
            this.description = description;
            this.limitsText = limitsText;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }

        public String getLimitsText() {
            return limitsText;
        }

        public boolean hasLimits() {
            return limitsText != null && !limitsText.trim().isEmpty();
        }
    }
}