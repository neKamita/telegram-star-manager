package shit.back.telegram.ui.strategy;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import shit.back.application.balance.dto.response.SimpleBalanceResponse;
import shit.back.domain.balance.valueobjects.Money;
import shit.back.telegram.ui.TelegramUIResponse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Упрощенная стратегия покупки звезд с единым балансом
 * 
 * Заменяет сложную логику трансферов простым процессом покупки
 * напрямую из единого баланса пользователя.
 * Следует принципам SOLID, DRY, Clean Code, KISS.
 */
@Component
public class SimplifiedStarPurchaseStrategy {

    private static final String STAR_EMOJI = "⭐";
    private static final String MONEY_EMOJI = "💰";
    private static final String WARNING_EMOJI = "⚠️";
    private static final String SUCCESS_EMOJI = "✅";

    /**
     * Создание упрощенного потока покупки звезд
     * 
     * @param balance баланс пользователя
     * @return TelegramUIResponse с интерфейсом покупки
     */
    public TelegramUIResponse createStarPurchaseFlow(SimpleBalanceResponse balance) {
        if (balance == null) {
            throw new IllegalArgumentException("SimpleBalanceResponse не может быть null");
        }

        if (!balance.isActive()) {
            return createInactiveBalanceMessage(balance.getUserId());
        }

        if (!balance.getCurrentBalance().isPositive()) {
            return createInsufficientFundsMessage(balance);
        }

        return createPurchaseMenu(balance);
    }

    /**
     * Получение доступных пакетов звезд для покупки
     * 
     * @param userBalance баланс пользователя
     * @return список доступных пакетов
     */
    public List<StarPackageOption> getAvailablePackages(Money userBalance) {
        List<StarPackageOption> packages = new ArrayList<>();

        // Базовые пакеты звезд
        addPackageIfAffordable(packages, userBalance, 100, Money.of("1.00"));
        addPackageIfAffordable(packages, userBalance, 500, Money.of("4.50"));
        addPackageIfAffordable(packages, userBalance, 1000, Money.of("8.00"));
        addPackageIfAffordable(packages, userBalance, 2500, Money.of("18.00"));
        addPackageIfAffordable(packages, userBalance, 5000, Money.of("35.00"));
        addPackageIfAffordable(packages, userBalance, 10000, Money.of("65.00"));

        return packages;
    }

    /**
     * Создание меню покупки с доступными пакетами
     */
    private TelegramUIResponse createPurchaseMenu(SimpleBalanceResponse balance) {
        List<StarPackageOption> availablePackages = getAvailablePackages(balance.getCurrentBalance());

        StringBuilder message = new StringBuilder();
        message.append("<b>").append(STAR_EMOJI).append(" Покупка Telegram Stars</b>\n\n");
        message.append(MONEY_EMOJI).append(" <b>Ваш баланс:</b> ")
                .append(balance.getFormattedBalance()).append("\n\n");

        if (availablePackages.isEmpty()) {
            message.append(WARNING_EMOJI).append(" <i>Недостаточно средств для покупки звезд</i>\n");
            message.append("Минимальная стоимость пакета: 1.00 $");
        } else {
            message.append("Выберите пакет звезд для покупки:\n\n");

            for (StarPackageOption pkg : availablePackages) {
                message.append(String.format("⭐ <b>%d звезд</b> - %s\n",
                        pkg.getStarCount(), pkg.getPrice().getFormattedAmount() + " $"));
            }

            message.append("\n💡 <i>Звезды списываются мгновенно с баланса</i>");
        }

        InlineKeyboardMarkup keyboard = createPurchaseKeyboard(availablePackages, balance);

        return TelegramUIResponse.newMessage(balance.getUserId(), message.toString())
                .keyboard(keyboard)
                .parseMode("HTML")
                .build();
    }

    /**
     * Создание клавиатуры для покупки звезд
     */
    private InlineKeyboardMarkup createPurchaseKeyboard(List<StarPackageOption> packages,
            SimpleBalanceResponse balance) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Кнопки пакетов (по 2 в ряду)
        for (int i = 0; i < packages.size(); i += 2) {
            List<InlineKeyboardButton> row = new ArrayList<>();

            StarPackageOption pkg1 = packages.get(i);
            row.add(createPackageButton(pkg1));

            if (i + 1 < packages.size()) {
                StarPackageOption pkg2 = packages.get(i + 1);
                row.add(createPackageButton(pkg2));
            }

            rows.add(row);
        }

        // Дополнительные кнопки
        List<InlineKeyboardButton> controlRow = new ArrayList<>();
        controlRow.add(createButton("💳 Пополнить баланс", "topup_balance"));
        controlRow.add(createButton("↩️ Назад", "back_to_balance"));
        rows.add(controlRow);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    /**
     * Создание кнопки для пакета звезд
     */
    private InlineKeyboardButton createPackageButton(StarPackageOption pkg) {
        String buttonText = String.format("%d ⭐ - %s $",
                pkg.getStarCount(),
                pkg.getPrice().getFormattedAmount());
        String callbackData = String.format("buy_stars_%d_%s",
                pkg.getStarCount(),
                pkg.getPrice().getAmount().toPlainString());

        return createButton(buttonText, callbackData);
    }

    /**
     * Создание обычной кнопки
     */
    private InlineKeyboardButton createButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }

    /**
     * Добавление пакета в список, если пользователь может его купить
     */
    private void addPackageIfAffordable(List<StarPackageOption> packages, Money userBalance,
            Integer starCount, Money price) {
        if (userBalance.isGreaterThanOrEqual(price)) {
            packages.add(new StarPackageOption(starCount, price));
        }
    }

    /**
     * Сообщение о неактивном балансе
     */
    private TelegramUIResponse createInactiveBalanceMessage(Long userId) {
        String messageText = WARNING_EMOJI + " <b>Баланс заблокирован</b>\n\n" +
                "Ваш баланс временно заблокирован.\n" +
                "Обратитесь в службу поддержки для разблокировки.\n\n" +
                "<i>Покупка звезд недоступна</i>";

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        InlineKeyboardButton supportButton = createButton("💬 Поддержка", "contact_support");
        keyboard.setKeyboard(List.of(List.of(supportButton)));

        return TelegramUIResponse.newMessage(userId, messageText)
                .keyboard(keyboard)
                .parseMode("HTML")
                .build();
    }

    /**
     * Сообщение о недостаточности средств
     */
    private TelegramUIResponse createInsufficientFundsMessage(SimpleBalanceResponse balance) {
        String messageText = WARNING_EMOJI + " <b>Недостаточно средств</b>\n\n" +
                MONEY_EMOJI + " Ваш баланс: " + balance.getFormattedBalance() + "\n\n" +
                "Для покупки звезд необходимо пополнить баланс.\n" +
                "Минимальная стоимость пакета: 1.00 $\n\n" +
                "<i>Пополните баланс и возвращайтесь!</i>";

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<InlineKeyboardButton> row = List.of(
                createButton("💳 Пополнить", "topup_balance"),
                createButton("↩️ Назад", "back_to_balance"));
        keyboard.setKeyboard(List.of(row));

        return TelegramUIResponse.newMessage(balance.getUserId(), messageText)
                .keyboard(keyboard)
                .parseMode("HTML")
                .build();
    }

    /**
     * Опция пакета звезд для покупки
     */
    public static class StarPackageOption {
        private final Integer starCount;
        private final Money price;

        public StarPackageOption(Integer starCount, Money price) {
            this.starCount = starCount;
            this.price = price;
        }

        public Integer getStarCount() {
            return starCount;
        }

        public Money getPrice() {
            return price;
        }

        @Override
        public String toString() {
            return String.format("StarPackageOption{stars=%d, price=%s}",
                    starCount, price.getFormattedAmount());
        }
    }
}