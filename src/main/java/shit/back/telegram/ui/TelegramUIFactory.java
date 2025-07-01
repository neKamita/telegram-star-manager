package shit.back.telegram.ui;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import shit.back.application.balance.dto.response.SimpleBalanceResponse;
import shit.back.config.StarPriceConstants;
import shit.back.domain.balance.valueobjects.Currency;
import shit.back.domain.balance.valueobjects.Money;
import shit.back.telegram.ui.builder.AdvancedKeyboardBuilder;
import shit.back.telegram.ui.builder.TelegramMessageBuilder;
import shit.back.telegram.ui.builder.TelegramKeyboardBuilder;
import shit.back.telegram.ui.factory.TelegramKeyboardFactory;
import shit.back.telegram.ui.factory.TelegramMessageFactory;
import shit.back.telegram.ui.strategy.BalanceDisplayStrategy;
import shit.back.telegram.ui.strategy.SimplifiedBalanceDisplayStrategy;
import shit.back.telegram.ui.strategy.SimplifiedStarPurchaseStrategy;
import shit.back.telegram.ui.strategy.WelcomeCardStrategy;

/**
 * Главная UI Factory для создания Telegram компонентов
 *
 * Упрощенная версия без legacy кода StarPurchaseFlowStrategy.
 * Следует принципам SOLID, DRY, Clean Code, KISS.
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
        private BalanceDisplayStrategy balanceDisplayStrategy;

        @Autowired
        private SimplifiedBalanceDisplayStrategy simplifiedBalanceDisplayStrategy;

        @Autowired
        private SimplifiedStarPurchaseStrategy simplifiedStarPurchaseStrategy;

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

        /**
         * Получить AdvancedKeyboardBuilder
         */
        public AdvancedKeyboardBuilder advancedKeyboardBuilder() {
                return advancedKeyboardBuilder;
        }

        // === ОСНОВНЫЕ UI КОМПОНЕНТЫ ===

        /**
         * Создать приветственное сообщение
         */
        public TelegramUIResponse createWelcomeMessage(Long chatId, String userName, SimpleBalanceResponse balance) {
                String welcomeText = String.format("""
                                🎉 <b>Добро пожаловать, %s!</b>

                                💰 Ваш баланс: %s
                                🌟 Готов к покупке звезд Telegram!

                                Используйте меню ниже для навигации.
                                """,
                                userName != null ? userName : "пользователь",
                                balance != null ? balance.getFormattedBalance() : "0.00 $");

                InlineKeyboardMarkup keyboard = keyboardFactory.createMainMenu();

                return messageBuilder()
                                .chatId(chatId)
                                .text(welcomeText)
                                .keyboard(keyboard)
                                .build();
        }

        /**
         * Создать сообщение с балансом
         */
        public TelegramUIResponse createBalanceMessage(Long chatId, SimpleBalanceResponse balance) {
                String balanceText = balanceDisplayStrategy.formatContent("BALANCE_INFO", balance);
                InlineKeyboardMarkup keyboard = advancedKeyboardBuilder.createBalanceActionsKeyboard(
                                balance.getCurrentBalance().isPositive(), false);

                return messageBuilder()
                                .chatId(chatId)
                                .text(balanceText)
                                .keyboard(keyboard)
                                .build();
        }

        /**
         * Создать упрощенную карточку баланса
         */
        public TelegramUIResponse createSimplifiedBalanceDisplay(Long chatId, SimpleBalanceResponse balance) {
                var uiResponse = simplifiedBalanceDisplayStrategy.createBalanceDisplay(balance);

                return TelegramUIResponse.newMessage(chatId, uiResponse.getMessageText())
                                .keyboard(uiResponse.getKeyboard())
                                .parseMode(uiResponse.getParseMode())
                                .build();
        }

        /**
         * Создать интерфейс покупки звезд (упрощенный)
         */
        public TelegramUIResponse createStarPurchaseInterface(Long chatId, SimpleBalanceResponse balance) {
                var uiResponse = simplifiedStarPurchaseStrategy.createStarPurchaseFlow(balance);

                return TelegramUIResponse.newMessage(chatId, uiResponse.getMessageText())
                                .keyboard(uiResponse.getKeyboard())
                                .parseMode(uiResponse.getParseMode())
                                .build();
        }

        /**
         * Создать подтверждение покупки звезд
         */
        public TelegramUIResponse createStarPurchaseConfirmation(Long chatId, int stars, Money amount,
                        String currencySymbol, SimpleBalanceResponse balance) {
                String confirmationCard = String.format("""
                                ⭐ <b>Подтверждение покупки</b>

                                🌟 Звезды: %d
                                💰 Стоимость: %s %s
                                💵 Баланс: %s

                                Подтвердите покупку:
                                """,
                                stars,
                                amount.getFormattedAmount(),
                                currencySymbol,
                                balance.getFormattedBalance());

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
         * Создать историю покупок
         */
        public TelegramUIResponse createPurchaseHistory(Long chatId, String historyText, int page, boolean hasNext) {
                String historyCard = String.format("""
                                📋 <b>История покупок</b>

                                %s

                                📄 Страница: %d
                                """,
                                historyText != null ? historyText : "История пуста",
                                page + 1);

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
         * Создать сообщение о недостатке средств (упрощенное)
         */
        public TelegramUIResponse createInsufficientFundsMessage(Long chatId, SimpleBalanceResponse balance,
                        Money required, int requestedStars) {
                String currencySymbol = balance.getCurrency().getSymbol();
                Money shortfall = required.subtract(balance.getCurrentBalance());

                String insufficientText = String.format("""
                                ❌ <b>Недостаточно средств</b>

                                ⭐ <b>Запрошено:</b> %d звезд
                                💰 <b>Требуется:</b> %s %s
                                💼 <b>Доступно:</b> %s
                                💸 <b>Не хватает:</b> %s %s

                                💡 Пополните баланс и возвращайтесь!
                                """, requestedStars, required.getFormattedAmount(), currencySymbol,
                                balance.getFormattedBalance(), shortfall.getFormattedAmount(), currencySymbol);

                InlineKeyboardMarkup keyboard = advancedKeyboardBuilder.createBalanceActionsKeyboard(
                                balance.getCurrentBalance().isPositive(), false);

                return messageBuilder()
                                .chatId(chatId)
                                .text(insufficientText)
                                .keyboard(keyboard)
                                .build();
        }

        // === МЕТОДЫ ДОСТУПА К СТРАТЕГИЯМ ===

        /**
         * Получение упрощенной стратегии отображения баланса
         */
        public SimplifiedBalanceDisplayStrategy getSimplifiedBalanceDisplayStrategy() {
                return simplifiedBalanceDisplayStrategy;
        }

        /**
         * Получение упрощенной стратегии покупки звезд
         */
        public SimplifiedStarPurchaseStrategy getSimplifiedStarPurchaseStrategy() {
                return simplifiedStarPurchaseStrategy;
        }

        // === LEGACY МЕТОДЫ ДЛЯ СОВМЕСТИМОСТИ ===

        /**
         * @deprecated Используйте createWelcomeMessage(Long, String,
         *             SimpleBalanceResponse)
         */
        @Deprecated
        public TelegramUIResponse createWelcomeMessage(Long chatId, String userName) {
                return createWelcomeMessage(chatId, userName, null);
        }

        /**
         * @deprecated Используйте createBalanceMessage(Long, SimpleBalanceResponse)
         */
        @Deprecated
        public TelegramUIResponse createBalanceMessage(Long chatId, Object balanceData, String userName) {
                if (balanceData instanceof SimpleBalanceResponse) {
                        return createBalanceMessage(chatId, (SimpleBalanceResponse) balanceData);
                }
                return createErrorMessage(chatId, "Неподдерживаемый тип данных баланса");
        }

        /**
         * @deprecated Используйте createTopupInterface(Long, Currency)
         */
        @Deprecated
        public TelegramUIResponse createTopupMessage(Long chatId, Object balanceData) {
                Currency currency = Currency.defaultCurrency();
                if (balanceData instanceof SimpleBalanceResponse) {
                        currency = ((SimpleBalanceResponse) balanceData).getCurrency();
                }
                return createTopupInterface(chatId, currency);
        }
}