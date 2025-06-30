package shit.back.telegram.ui;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import shit.back.application.balance.dto.response.DualBalanceResponse;
import shit.back.domain.balance.valueobjects.Currency;
import shit.back.domain.balance.valueobjects.Money;
import shit.back.telegram.ui.builder.AdvancedKeyboardBuilder;
import shit.back.telegram.ui.builder.RichMessageBuilder;
import shit.back.telegram.ui.builder.TelegramMessageBuilder;
import shit.back.telegram.ui.builder.TelegramKeyboardBuilder;
import shit.back.telegram.ui.factory.TelegramKeyboardFactory;
import shit.back.telegram.ui.factory.TelegramMessageFactory;
import shit.back.telegram.ui.strategy.BalanceDisplayStrategy;
import shit.back.telegram.ui.strategy.StarPurchaseFlowStrategy;
import shit.back.telegram.ui.strategy.WelcomeCardStrategy;

import java.util.List;

/**
 * Главная UI Factory для создания Telegram компонентов
 *
 * Объединяет функциональность всех UI компонентов в единую фабрику
 * с поддержкой расширенных клавиатур и богатых сообщений
 */
@Component
public class TelegramUIFactory {

    @Autowired
    private TelegramMessageFactory messageFactory;

    @Autowired
    private TelegramKeyboardFactory keyboardFactory;

    @Autowired
    private AdvancedKeyboardBuilder advancedKeyboardBuilder;

    @Autowired
    private RichMessageBuilder richMessageBuilder;

    @Autowired
    private BalanceDisplayStrategy balanceDisplayStrategy;

    @Autowired
    private StarPurchaseFlowStrategy starPurchaseFlowStrategy;

    @Autowired
    private WelcomeCardStrategy welcomeCardStrategy;

    // === БАЗОВЫЕ ФАБРИКИ ===

    /**
     * Получить MessageFactory
     */
    public TelegramMessageFactory messageFactory() {
        return messageFactory;
    }

    /**
     * Получить KeyboardFactory
     */
    public TelegramKeyboardFactory keyboardFactory() {
        return keyboardFactory;
    }

    /**
     * Создать новый MessageBuilder
     */
    public TelegramMessageBuilder messageBuilder() {
        return new TelegramMessageBuilder(messageFactory);
    }

    /**
     * Создать новый KeyboardBuilder
     */
    public TelegramKeyboardBuilder keyboardBuilder() {
        return new TelegramKeyboardBuilder();
    }

    // === РАСШИРЕННЫЕ BUILDERS ===

    /**
     * Получить AdvancedKeyboardBuilder
     */
    public AdvancedKeyboardBuilder advancedKeyboardBuilder() {
        return advancedKeyboardBuilder;
    }

    /**
     * Получить RichMessageBuilder
     */
    public RichMessageBuilder richMessageBuilder() {
        return richMessageBuilder;
    }

    // === READY-TO-USE UI КОМПОНЕНТЫ ===

    /**
     * Создать приветственное сообщение с красивой карточкой
     */
    public TelegramUIResponse createWelcomeMessage(Long chatId, String userName, DualBalanceResponse balance) {
        String welcomeText = richMessageBuilder.createWelcomeCard(userName, balance);
        InlineKeyboardMarkup keyboard = keyboardFactory.createMainMenu();

        return messageBuilder()
                .chatId(chatId)
                .text(welcomeText)
                .keyboard(keyboard)
                .build();
    }

    /**
     * Создать сообщение с балансом используя стратегию
     */
    public TelegramUIResponse createBalanceMessage(Long chatId, DualBalanceResponse balance) {
        String balanceText = balanceDisplayStrategy.formatContent("DUAL_BALANCE_INFO", balance);
        InlineKeyboardMarkup keyboard = advancedKeyboardBuilder.createBalanceActionsKeyboard(
                balance.hasMainFunds(), balance.hasBankFunds());

        return messageBuilder()
                .chatId(chatId)
                .text(balanceText)
                .keyboard(keyboard)
                .build();
    }

    /**
     * Создать красивую карточку баланса
     */
    public TelegramUIResponse createRichBalanceCard(Long chatId, DualBalanceResponse balance) {
        String balanceCard = richMessageBuilder.createBalanceCard(balance);
        InlineKeyboardMarkup keyboard = advancedKeyboardBuilder.createBalanceActionsKeyboard(
                balance.hasMainFunds(), balance.hasBankFunds());

        return messageBuilder()
                .chatId(chatId)
                .text(balanceCard)
                .keyboard(keyboard)
                .build();
    }

    /**
     * Создать интерфейс покупки звезд
     */
    public TelegramUIResponse createStarPurchaseInterface(Long chatId, DualBalanceResponse balance) {
        String purchaseText = starPurchaseFlowStrategy.formatContent("PURCHASE_INTERFACE", balance);
        InlineKeyboardMarkup keyboard = advancedKeyboardBuilder.createStarPackageKeyboard(
                balance.getCurrency(), balance.hasMainFunds());

        return messageBuilder()
                .chatId(chatId)
                .text(purchaseText)
                .keyboard(keyboard)
                .build();
    }

    /**
     * Создать подтверждение покупки звезд
     */
    public TelegramUIResponse createStarPurchaseConfirmation(Long chatId, int stars, Money amount,
            String currencySymbol, DualBalanceResponse balance) {
        String confirmationCard = richMessageBuilder.createPurchaseFlowCard(stars, amount, currencySymbol, balance);
        InlineKeyboardMarkup keyboard = advancedKeyboardBuilder.createPurchaseConfirmationKeyboard("stars",
                String.valueOf(stars));

        return messageBuilder()
                .chatId(chatId)
                .text(confirmationCard)
                .keyboard(keyboard)
                .build();
    }

    /**
     * Создать интерфейс пополнения баланса
     */
    public TelegramUIResponse createTopupInterface(Long chatId, Currency currency) {
        InlineKeyboardMarkup amountKeyboard = advancedKeyboardBuilder.createTopupAmountKeyboard(currency);
        String topupText = String.format("""
                💳 <b>Пополнение баланса</b>

                💱 Валюта: %s

                Выберите сумму пополнения или введите свою:
                """, currency.getFormattedName());

        return messageBuilder()
                .chatId(chatId)
                .text(topupText)
                .keyboard(amountKeyboard)
                .build();
    }

    /**
     * Создать выбор способа оплаты
     */
    public TelegramUIResponse createPaymentMethodSelection(Long chatId, Currency currency, Money amount) {
        InlineKeyboardMarkup methodKeyboard = advancedKeyboardBuilder.createPaymentMethodKeyboard(currency);
        String methodText = String.format("""
                💳 <b>Способ оплаты</b>

                💰 Сумма: %s %s
                💱 Валюта: %s

                Выберите удобный способ оплаты:
                """, amount.getFormattedAmount(), currency.getSymbol(), currency.getFormattedName());

        return messageBuilder()
                .chatId(chatId)
                .text(methodText)
                .keyboard(methodKeyboard)
                .build();
    }

    /**
     * Создать подтверждение перевода средств
     */
    public TelegramUIResponse createTransferConfirmation(Long chatId, Money amount, String currencySymbol,
            DualBalanceResponse balance) {
        String transferCard = richMessageBuilder.createTransferConfirmationCard(amount, currencySymbol, balance);
        InlineKeyboardMarkup keyboard = advancedKeyboardBuilder.createTransferConfirmationKeyboard(
                amount.getFormattedAmount());

        return messageBuilder()
                .chatId(chatId)
                .text(transferCard)
                .keyboard(keyboard)
                .build();
    }

    /**
     * Создать историю покупок
     */
    public TelegramUIResponse createPurchaseHistory(Long chatId, List<RichMessageBuilder.PurchaseHistoryItem> purchases,
            int page, boolean hasNext) {
        String historyCard = richMessageBuilder.createPurchaseHistoryCard(purchases, page, hasNext);
        InlineKeyboardMarkup keyboard = advancedKeyboardBuilder.createHistoryNavigationKeyboard(
                "purchases", page, hasNext, page > 0);

        return messageBuilder()
                .chatId(chatId)
                .text(historyCard)
                .keyboard(keyboard)
                .build();
    }

    /**
     * Создать сообщение об ошибке
     */
    public TelegramUIResponse createErrorMessage(Long chatId, String errorText) {
        return messageBuilder()
                .chatId(chatId)
                .text("❌ <b>Ошибка</b>\n\n" + errorText)
                .keyboard(keyboardFactory.createBackToMain())
                .build();
    }

    /**
     * Создать сообщение о недостатке средств
     */
    public TelegramUIResponse createInsufficientFundsMessage(Long chatId, DualBalanceResponse balance,
            Money required, int requestedStars) {
        StarPurchaseFlowStrategy.InsufficientFundsData data = new StarPurchaseFlowStrategy.InsufficientFundsData(
                balance, required, requestedStars);

        String insufficientText = starPurchaseFlowStrategy.formatContent("INSUFFICIENT_FUNDS", data);
        InlineKeyboardMarkup keyboard = advancedKeyboardBuilder.createBalanceActionsKeyboard(
                balance.hasMainFunds(), balance.hasBankFunds());

        return messageBuilder()
                .chatId(chatId)
                .text(insufficientText)
                .keyboard(keyboard)
                .build();
    }

    // === LEGACY МЕТОДЫ ДЛЯ СОВМЕСТИМОСТИ ===

    /**
     * @deprecated Используйте createWelcomeMessage(Long, String,
     *             DualBalanceResponse)
     */
    @Deprecated
    public TelegramUIResponse createWelcomeMessage(Long chatId, String userName) {
        return createWelcomeMessage(chatId, userName, null);
    }

    /**
     * @deprecated Используйте createBalanceMessage(Long, DualBalanceResponse)
     */
    @Deprecated
    public TelegramUIResponse createBalanceMessage(Long chatId, Object balanceData, String userName) {
        if (balanceData instanceof DualBalanceResponse) {
            return createBalanceMessage(chatId, (DualBalanceResponse) balanceData);
        }
        return createErrorMessage(chatId, "Неподдерживаемый тип данных баланса");
    }

    /**
     * @deprecated Используйте createTopupInterface(Long, Currency)
     */
    @Deprecated
    public TelegramUIResponse createTopupMessage(Long chatId, Object balanceData) {
        Currency currency = Currency.defaultCurrency();
        if (balanceData instanceof DualBalanceResponse) {
            currency = ((DualBalanceResponse) balanceData).getCurrency();
        }
        return createTopupInterface(chatId, currency);
    }
}