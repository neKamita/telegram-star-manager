package shit.back.telegram.ui.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import shit.back.application.balance.dto.response.SimpleBalanceResponse;
import shit.back.config.PaymentConfigurationProperties;
import shit.back.domain.balance.valueobjects.Money;
import shit.back.telegram.ui.strategy.utils.PaymentMethodsHelper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Стратегия интерфейса покупки звезд
 *
 * Управляет флоу покупки звезд с проверкой баланса и объяснением
 * что бот покупает звезды ЗА пользователя через Fragment API
 */
@Component
@Slf4j
public class StarPurchaseFlowStrategy implements TelegramMessageStrategy {

    private static final String STRATEGY_TYPE = "STAR_PURCHASE_FLOW";
    private static final String[] SUPPORTED_TYPES = {
            "PURCHASE_INTERFACE", "BALANCE_CHECK", "PURCHASE_CONFIRMATION", "INSUFFICIENT_FUNDS"
    };

    // Популярные пакеты звезд с ценами (примерные курсы)
    private static final StarPackage[] STAR_PACKAGES = {
            new StarPackage(100, new BigDecimal("1.00")),
            new StarPackage(250, new BigDecimal("2.50")),
            new StarPackage(500, new BigDecimal("5.00")),
            new StarPackage(1000, new BigDecimal("10.00")),
            new StarPackage(2500, new BigDecimal("25.00"))
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
            case "PURCHASE_INTERFACE" -> formatPurchaseInterface(data);
            case "BALANCE_CHECK" -> formatBalanceCheck(data);
            case "PURCHASE_CONFIRMATION" -> formatPurchaseConfirmation(data);
            case "INSUFFICIENT_FUNDS" -> formatInsufficientFunds(data);
            default -> throw new IllegalArgumentException("Неподдерживаемый тип контента: " + contentType);
        };
    }

    /**
     * Основной интерфейс покупки звезд
     */
    private String formatPurchaseInterface(Object data) {
        if (!(data instanceof SimpleBalanceResponse balance)) {
            throw new IllegalArgumentException("Ожидался SimpleBalanceResponse для PURCHASE_INTERFACE");
        }

        // ДИАГНОСТИЧЕСКИЙ ЛОГ: Упрощенная информация о балансе
        log.debug(
                "🔍 ДИАГНОСТИКА StarPurchaseFlow: userId={}, currentBalance={}",
                balance.getUserId(),
                balance.getFormattedBalance());

        StringBuilder message = new StringBuilder();
        String currencySymbol = balance.getCurrency().getSymbol();

        message.append("⭐ <b>Покупка Telegram Stars</b>\n\n");

        // Состояние баланса
        message.append("💰 <b>Ваш баланс:</b>\n");
        message.append(String.format("💵 Доступно: %s\n\n", balance.getFormattedBalance()));

        // Проверка готовности к покупке
        if (!balance.getCurrentBalance().isPositive()) {
            message.append("⚠️ <b>Недостаточно средств</b>\n");
            message.append("Пополните баланс для покупки звезд\n\n");
        } else {
            message.append("✅ <b>Готово к покупке!</b>\n\n");
        }

        // Доступные пакеты звезд
        message.append("📦 <b>Доступные пакеты:</b>\n");
        List<StarPackage> availablePackages = getAvailablePackages(balance);

        if (availablePackages.isEmpty()) {
            message.append("❌ <i>Недостаточно средств для покупки звезд</i>\n\n");
        } else {
            for (StarPackage pkg : availablePackages) {
                boolean canAfford = balance.hasSufficientFunds(Money.of(pkg.price));
                String statusIcon = canAfford ? "✅" : "❌";

                message.append(String.format("%s ⭐<b>%d</b> за %s %s\n",
                        statusIcon, pkg.stars, pkg.price, currencySymbol));
            }
            message.append("\n");
        }

        // Объяснение процесса
        message.append("🤖 <b>Как происходит покупка:</b>\n");
        message.append("1️⃣ Вы выбираете пакет звезд\n");
        message.append("2️⃣ Бот списывает средства с вашего рабочего баланса\n");
        message.append("3️⃣ Бот покупает звезды\n");
        message.append("4️⃣ Звезды зачисляются на ваш Telegram аккаунт\n\n");

        message.append("💡 <i>Покупка выполняется автоматически через корпоративный аккаунт</i>");

        return message.toString();
    }

    /**
     * Проверка баланса перед покупкой
     */
    private String formatBalanceCheck(Object data) {
        if (!(data instanceof BalanceCheckData checkData)) {
            throw new IllegalArgumentException("Ожидался BalanceCheckData для BALANCE_CHECK");
        }

        SimpleBalanceResponse balance = checkData.balance;
        int requestedStars = checkData.requestedStars;
        Money requiredAmount = checkData.requiredAmount;

        StringBuilder message = new StringBuilder();
        String currencySymbol = balance.getCurrency().getSymbol();

        message.append("🔍 <b>Проверка баланса</b>\n\n");
        message.append(String.format("⭐ <b>Запрошено:</b> %d звезд\n", requestedStars));
        message.append(String.format("💰 <b>Требуется:</b> %s %s\n\n",
                requiredAmount.getFormattedAmount(), currencySymbol));

        // Упрощенная проверка баланса
        message.append("💰 <b>Состояние баланса:</b>\n");
        message.append(String.format("💵 Доступно: %s\n\n", balance.getFormattedBalance()));

        // Результат проверки
        if (balance.hasSufficientFunds(requiredAmount)) {
            message.append("✅ <b>Средств достаточно!</b>\n");
            message.append("Покупка может быть выполнена немедленно\n\n");

            Money remainingAfter = balance.getCurrentBalance().subtract(requiredAmount);
            message.append(String.format("💼 <b>Остаток после покупки:</b> %s %s\n",
                    remainingAfter.getFormattedAmount(), currencySymbol));
        } else {
            message.append("❌ <b>Недостаточно средств</b>\n");
            Money shortfall = requiredAmount.subtract(balance.getCurrentBalance());
            message.append(String.format("💸 <b>Не хватает:</b> %s %s\n",
                    shortfall.getFormattedAmount(), currencySymbol));
        }

        return message.toString();
    }

    /**
     * Подтверждение покупки
     */
    private String formatPurchaseConfirmation(Object data) {
        if (!(data instanceof PurchaseConfirmationData confirmData)) {
            throw new IllegalArgumentException("Ожидался PurchaseConfirmationData для PURCHASE_CONFIRMATION");
        }

        int stars = confirmData.stars;
        Money amount = confirmData.amount;
        String currencySymbol = confirmData.currencySymbol;

        return String.format("""
                ✅ <b>Подтверждение покупки</b>

                ⭐ <b>Звезд:</b> %d
                💰 <b>Сумма:</b> %s %s

                🤖 <b>Что произойдет:</b>
                • Средства будут списаны с рабочего баланса
                • Бот купит звезды через Telegram Fragment
                • Звезды поступят на ваш аккаунт

                ⚠️ <b>Внимание:</b> Операция необратима

                Подтвердите покупку для продолжения
                """, stars, amount.getFormattedAmount(), currencySymbol);
    }

    /**
     * Сообщение о недостатке средств
     */
    private String formatInsufficientFunds(Object data) {
        if (!(data instanceof InsufficientFundsData fundsData)) {
            throw new IllegalArgumentException("Ожидался InsufficientFundsData для INSUFFICIENT_FUNDS");
        }

        SimpleBalanceResponse balance = fundsData.balance;
        Money required = fundsData.required;
        int requestedStars = fundsData.requestedStars;

        StringBuilder message = new StringBuilder();
        String currencySymbol = balance.getCurrency().getSymbol();

        message.append("❌ <b>Недостаточно средств</b>\n\n");
        message.append(String.format("⭐ <b>Запрошено:</b> %d звезд\n", requestedStars));
        message.append(String.format("💰 <b>Требуется:</b> %s %s\n",
                required.getFormattedAmount(), currencySymbol));
        message.append(String.format("💼 <b>Доступно:</b> %s\n\n", balance.getFormattedBalance()));

        Money shortfall = required.subtract(balance.getCurrentBalance());
        message.append(String.format("💸 <b>Не хватает:</b> %s %s\n\n",
                shortfall.getFormattedAmount(), currencySymbol));

        // Рекомендации по пополнению
        message.append("💡 <b>Рекомендации:</b>\n");
        message.append("• Пополните баланс одним из способов:\n");

        List<String> paymentMethods = getAvailablePaymentMethods(balance);
        for (String method : paymentMethods) {
            message.append(String.format("  - %s\n", method));
        }

        if (paymentMethods.isEmpty()) {
            message.append("  - Обратитесь к администратору\n");
        }

        message.append("\n🤖 <i>После пополнения бот сможет купить звезды за вас</i>");

        return message.toString();
    }

    /**
     * Получение доступных пакетов звезд на основе баланса
     */
    private List<StarPackage> getAvailablePackages(SimpleBalanceResponse balance) {
        List<StarPackage> available = new ArrayList<>();

        for (StarPackage pkg : STAR_PACKAGES) {
            Money packagePrice = Money.of(pkg.price);

            // Показываем все пакеты, но помечаем доступность
            if (pkg.stars >= paymentConfig.getFragment().getMinStarsAmount() &&
                    pkg.stars <= paymentConfig.getFragment().getMaxStarsAmount()) {
                available.add(pkg);
            }
        }

        return available;
    }

    /**
     * Получение доступных способов оплаты
     */
    private List<String> getAvailablePaymentMethods(SimpleBalanceResponse balance) {
        return PaymentMethodsHelper.getAvailablePaymentMethods(balance.getCurrency(), paymentConfig);
    }

    // Вспомогательные классы для передачи данных

    public static class BalanceCheckData {
        public final SimpleBalanceResponse balance;
        public final int requestedStars;
        public final Money requiredAmount;

        public BalanceCheckData(SimpleBalanceResponse balance, int requestedStars, Money requiredAmount) {
            this.balance = balance;
            this.requestedStars = requestedStars;
            this.requiredAmount = requiredAmount;
        }
    }

    public static class PurchaseConfirmationData {
        public final int stars;
        public final Money amount;
        public final String currencySymbol;

        public PurchaseConfirmationData(int stars, Money amount, String currencySymbol) {
            this.stars = stars;
            this.amount = amount;
            this.currencySymbol = currencySymbol;
        }
    }

    public static class InsufficientFundsData {
        public final SimpleBalanceResponse balance;
        public final Money required;
        public final int requestedStars;

        public InsufficientFundsData(SimpleBalanceResponse balance, Money required, int requestedStars) {
            this.balance = balance;
            this.required = required;
            this.requestedStars = requestedStars;
        }
    }

    /**
     * Пакет звезд с ценой
     */
    private static class StarPackage {
        public final int stars;
        public final BigDecimal price;

        public StarPackage(int stars, BigDecimal price) {
            this.stars = stars;
            this.price = price;
        }
    }
}