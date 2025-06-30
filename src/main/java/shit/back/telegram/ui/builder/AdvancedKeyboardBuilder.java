package shit.back.telegram.ui.builder;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import shit.back.config.PaymentConfigurationProperties;
import shit.back.domain.balance.valueobjects.Currency;
import shit.back.telegram.ui.CallbackDataConstants;
import shit.back.telegram.ui.strategy.utils.PaymentMethodsHelper;

import java.math.BigDecimal;
import java.util.List;

/**
 * Расширенный билдер клавиатур для Telegram интерфейса
 * 
 * Предоставляет готовые методы для создания сложных клавиатур
 * с динамическим контентом на основе конфигурации платежей
 */
@Component
public class AdvancedKeyboardBuilder {

    @Autowired
    private PaymentConfigurationProperties paymentConfig;

    /**
     * Создать клавиатуру действий с балансом
     */
    public InlineKeyboardMarkup createBalanceActionsKeyboard(boolean hasMainFunds, boolean hasBankFunds) {
        TelegramKeyboardBuilder builder = new TelegramKeyboardBuilder();

        // Основные действия с балансом
        builder.addButton("💰 Показать баланс", CallbackDataConstants.SHOW_BALANCE);
        builder.addButton("📊 Детали", CallbackDataConstants.BALANCE_DETAILS);
        builder.newRow();

        // Действия пополнения
        builder.addButton("💳 Пополнить", CallbackDataConstants.TOPUP_BALANCE);

        // Перевод средств (только если есть средства на пополненном балансе)
        if (hasBankFunds) {
            builder.addButton("🔄 Перевести в работу", CallbackDataConstants.TRANSFER_TO_MAIN);
        }
        builder.newRow();

        // Покупка звезд (только если есть средства в рабочем балансе)
        if (hasMainFunds) {
            builder.addButton("⭐ Купить звезды", CallbackDataConstants.BUY_STARS);
        } else {
            builder.addButton("⭐ Звезды (недоступно)", "stars:unavailable");
        }
        builder.newRow();

        // История и помощь
        builder.addButton("📈 История", CallbackDataConstants.BALANCE_HISTORY);
        builder.addButton("❓ Помощь", CallbackDataConstants.HELP_BALANCE);
        builder.newRow();

        // Возврат в главное меню
        builder.addButton("🏠 Главное меню", CallbackDataConstants.MAIN_MENU);

        return builder.build();
    }

    /**
     * Создать клавиатуру способов оплаты (только активные)
     */
    public InlineKeyboardMarkup createPaymentMethodKeyboard(Currency currency) {
        TelegramKeyboardBuilder builder = new TelegramKeyboardBuilder();

        List<PaymentMethodsHelper.PaymentMethodInfo> methods = PaymentMethodsHelper.getDetailedPaymentMethods(currency,
                paymentConfig);

        if (methods.isEmpty()) {
            builder.addButton("❌ Нет доступных способов", "payment:unavailable");
            builder.newRow();
            builder.addButton("🏠 Главное меню", CallbackDataConstants.MAIN_MENU);
            return builder.build();
        }

        // Добавляем активные способы оплаты
        for (PaymentMethodsHelper.PaymentMethodInfo method : methods) {
            String callbackData = extractPaymentMethodCallback(method.getDisplayName());
            builder.addButton(method.getDisplayName(), callbackData);
            builder.newRow();
        }

        // Навигация
        builder.addButton("🔙 Назад", CallbackDataConstants.BALANCE_MENU);
        builder.addButton("🏠 Главное меню", CallbackDataConstants.MAIN_MENU);

        return builder.build();
    }

    /**
     * Создать клавиатуру подтверждения покупки
     */
    public InlineKeyboardMarkup createPurchaseConfirmationKeyboard(String operationType, String operationId) {
        TelegramKeyboardBuilder builder = new TelegramKeyboardBuilder();

        // Подтверждение и отмена
        builder.addButton("✅ Подтвердить",
                CallbackDataConstants.confirmOperation(operationType, operationId));
        builder.addButton("❌ Отменить", CallbackDataConstants.CANCEL_OPERATION);
        builder.newRow();

        // Возврат назад
        builder.addButton("🔙 Назад", getBackCallbackForOperation(operationType));

        return builder.build();
    }

    /**
     * Создать клавиатуру навигации по истории
     */
    public InlineKeyboardMarkup createHistoryNavigationKeyboard(String historyType, int currentPage,
            boolean hasNextPage, boolean hasPrevPage) {
        TelegramKeyboardBuilder builder = new TelegramKeyboardBuilder();

        // Типы истории
        if (!"purchases".equals(historyType)) {
            builder.addButton("⭐ Покупки звезд", CallbackDataConstants.HISTORY_PURCHASES);
        }
        if (!"topups".equals(historyType)) {
            builder.addButton("💳 Пополнения", CallbackDataConstants.HISTORY_TOPUPS);
        }
        if (!"transfers".equals(historyType)) {
            builder.addButton("🔄 Переводы", CallbackDataConstants.HISTORY_TRANSFERS);
        }

        if (!historyType.equals("purchases") || !historyType.equals("topups") || !historyType.equals("transfers")) {
            builder.newRow();
        }

        // Пагинация
        if (hasPrevPage || hasNextPage) {
            if (hasPrevPage) {
                builder.addButton("⬅️ Предыдущая",
                        CallbackDataConstants.historyPage(historyType, currentPage - 1));
            }

            builder.addButton(String.format("📄 %d", currentPage + 1), "page:current");

            if (hasNextPage) {
                builder.addButton("➡️ Следующая",
                        CallbackDataConstants.historyPage(historyType, currentPage + 1));
            }
            builder.newRow();
        }

        // Навигация
        builder.addButton("🔙 К балансу", CallbackDataConstants.BALANCE_MENU);
        builder.addButton("🏠 Главное меню", CallbackDataConstants.MAIN_MENU);

        return builder.build();
    }

    /**
     * Создать клавиатуру выбора пакетов звезд
     */
    public InlineKeyboardMarkup createStarPackageKeyboard(Currency currency, boolean hasMainFunds) {
        TelegramKeyboardBuilder builder = new TelegramKeyboardBuilder();

        // Популярные пакеты звезд (используем данные из Fragment конфигурации)
        int minStars = paymentConfig.getFragment().getMinStarsAmount();
        int maxStars = paymentConfig.getFragment().getMaxStarsAmount();

        // Предопределенные пакеты в рамках лимитов
        int[] starPackages = { 100, 250, 500, 1000, 2500 };
        BigDecimal[] prices = {
                new BigDecimal("1.00"), new BigDecimal("2.50"),
                new BigDecimal("5.00"), new BigDecimal("10.00"), new BigDecimal("25.00")
        };

        boolean addedPackages = false;
        for (int i = 0; i < starPackages.length; i++) {
            int stars = starPackages[i];
            if (stars >= minStars && stars <= maxStars) {
                String statusIcon = hasMainFunds ? "⭐" : "🔒";
                String buttonText = String.format("%s %d за %s %s",
                        statusIcon, stars, prices[i], currency.getSymbol());
                String callbackData = CallbackDataConstants.starsPackage(stars, prices[i].toString());

                builder.addButton(buttonText, callbackData);
                builder.newRow();
                addedPackages = true;
            }
        }

        if (!addedPackages) {
            builder.addButton("❌ Пакеты недоступны", "stars:unavailable");
            builder.newRow();
        }

        // Пользовательская сумма
        if (hasMainFunds) {
            builder.addButton("✏️ Своя сумма", CallbackDataConstants.STARS_CUSTOM);
            builder.newRow();
        }

        // Навигация
        builder.addButton("🔙 К балансу", CallbackDataConstants.BALANCE_MENU);
        builder.addButton("🏠 Главное меню", CallbackDataConstants.MAIN_MENU);

        return builder.build();
    }

    /**
     * Создать клавиатуру быстрых сумм для пополнения
     */
    public InlineKeyboardMarkup createTopupAmountKeyboard(Currency currency) {
        TelegramKeyboardBuilder builder = new TelegramKeyboardBuilder();

        // Быстрые суммы в зависимости от валюты
        String[] amounts = getQuickAmounts(currency);
        String symbol = currency.getSymbol();

        // Добавляем быстрые суммы (по 2 в ряд)
        for (int i = 0; i < amounts.length; i += 2) {
            builder.addButton(symbol + " " + amounts[i],
                    CallbackDataConstants.topupAmount(amounts[i]));

            if (i + 1 < amounts.length) {
                builder.addButton(symbol + " " + amounts[i + 1],
                        CallbackDataConstants.topupAmount(amounts[i + 1]));
            }
            builder.newRow();
        }

        // Пользовательская сумма
        builder.addButton("✏️ Своя сумма", CallbackDataConstants.TOPUP_CUSTOM);
        builder.newRow();

        // Навигация
        builder.addButton("🔙 Назад", CallbackDataConstants.BALANCE_MENU);
        builder.addButton("🏠 Главное меню", CallbackDataConstants.MAIN_MENU);

        return builder.build();
    }

    /**
     * Создать клавиатуру подтверждения перевода средств
     */
    public InlineKeyboardMarkup createTransferConfirmationKeyboard(String amount) {
        TelegramKeyboardBuilder builder = new TelegramKeyboardBuilder();

        builder.addButton("✅ Перевести",
                CallbackDataConstants.transferAmount(amount));
        builder.addButton("❌ Отменить", CallbackDataConstants.CANCEL_OPERATION);
        builder.newRow();

        builder.addButton("🔙 К балансу", CallbackDataConstants.BALANCE_MENU);

        return builder.build();
    }

    /**
     * Извлечение callback data для способа оплаты
     */
    private String extractPaymentMethodCallback(String displayName) {
        if (displayName.contains("TON")) {
            return CallbackDataConstants.PAYMENT_TON;
        } else if (displayName.contains("YooKassa")) {
            return CallbackDataConstants.PAYMENT_YOOKASSA;
        } else if (displayName.contains("UZS")) {
            return CallbackDataConstants.PAYMENT_UZS;
        }
        return CallbackDataConstants.PAYMENT_PREFIX + ":unknown";
    }

    /**
     * Получение callback для возврата назад в зависимости от операции
     */
    private String getBackCallbackForOperation(String operationType) {
        return switch (operationType) {
            case "stars" -> CallbackDataConstants.STARS_MENU;
            case "topup" -> CallbackDataConstants.TOPUP_BALANCE;
            case "transfer" -> CallbackDataConstants.BALANCE_MENU;
            default -> CallbackDataConstants.MAIN_MENU;
        };
    }

    /**
     * Получение быстрых сумм для пополнения в зависимости от валюты
     */
    private String[] getQuickAmounts(Currency currency) {
        if (currency.isUsd()) {
            return new String[] { "5", "10", "25", "50", "100", "250" };
        } else if (currency.isUzs()) {
            return new String[] { "50000", "100000", "250000", "500000", "1000000", "2500000" };
        }
        // Для других валют используем USD суммы
        return new String[] { "5", "10", "25", "50", "100", "250" };
    }
}