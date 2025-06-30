package shit.back.telegram.ui.strategy;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import shit.back.application.balance.dto.response.SimpleBalanceResponse;
import shit.back.telegram.ui.TelegramUIResponse;

import java.util.List;

/**
 * Упрощенная стратегия отображения единого баланса
 * 
 * Заменяет сложное отображение DualBalance простым и понятным интерфейсом
 * с единым балансом пользователя.
 * Следует принципам SOLID, DRY, Clean Code, KISS.
 */
@Component
public class SimplifiedBalanceDisplayStrategy {

    private static final String BALANCE_EMOJI = "💰";
    private static final String STATUS_ACTIVE_EMOJI = "✅";
    private static final String STATUS_INACTIVE_EMOJI = "❌";
    private static final String UPDATED_EMOJI = "🕐";

    /**
     * Создание простого отображения баланса пользователя
     * 
     * @param balance баланс пользователя
     * @return TelegramUIResponse с отформатированным сообщением и клавиатурой
     */
    public TelegramUIResponse createBalanceDisplay(SimpleBalanceResponse balance) {
        if (balance == null) {
            throw new IllegalArgumentException("SimpleBalanceResponse не может быть null");
        }

        String messageText = formatSimpleBalance(balance);
        InlineKeyboardMarkup keyboard = createBalanceKeyboard(balance);

        return TelegramUIResponse.newMessage(balance.getUserId(), messageText)
                .keyboard(keyboard)
                .parseMode("HTML")
                .build();
    }

    /**
     * Форматирование простого баланса в читаемую строку
     * 
     * @param balance баланс пользователя
     * @return отформатированная строка баланса
     */
    public String formatSimpleBalance(SimpleBalanceResponse balance) {
        if (balance == null) {
            return "❌ Ошибка получения баланса";
        }

        StringBuilder message = new StringBuilder();

        // Заголовок
        message.append("<b>").append(BALANCE_EMOJI).append(" Ваш баланс</b>\n\n");

        // Основная информация о балансе
        message.append("💵 <b>Текущий баланс:</b> ")
                .append(balance.getFormattedBalance())
                .append("\n");

        // Статус баланса
        String statusEmoji = balance.isActive() ? STATUS_ACTIVE_EMOJI : STATUS_INACTIVE_EMOJI;
        String statusText = balance.isActive() ? "Активен" : "Заблокирован";
        message.append("📊 <b>Статус:</b> ")
                .append(statusEmoji)
                .append(" ")
                .append(statusText)
                .append("\n");

        // Дата последнего обновления
        message.append(UPDATED_EMOJI)
                .append(" <b>Обновлен:</b> ")
                .append(balance.getFormattedLastUpdated())
                .append("\n\n");

        // Подсказка для пользователя
        if (balance.isActive()) {
            if (balance.getCurrentBalance().isPositive()) {
                message.append("🌟 <i>Вы можете купить звезды Telegram!</i>");
            } else {
                message.append("💳 <i>Пополните баланс для покупки звезд</i>");
            }
        } else {
            message.append("⚠️ <i>Баланс заблокирован. Обратитесь в поддержку.</i>");
        }

        return message.toString();
    }

    /**
     * Создание клавиатуры для управления балансом
     */
    private InlineKeyboardMarkup createBalanceKeyboard(SimpleBalanceResponse balance) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();

        // Первый ряд - основные действия
        InlineKeyboardButton topupButton = createButton("💳 Пополнить", "topup_balance");
        InlineKeyboardButton historyButton = createButton("📋 История", "balance_history");

        // Второй ряд - покупка звезд (если баланс позволяет)
        if (balance.isActive() && balance.getCurrentBalance().isPositive()) {
            InlineKeyboardButton buyStarsButton = createButton("🌟 Купить звезды", "buy_stars");
            keyboard.setKeyboard(List.of(
                    List.of(topupButton, historyButton),
                    List.of(buyStarsButton)));
        } else {
            keyboard.setKeyboard(List.of(
                    List.of(topupButton, historyButton)));
        }

        return keyboard;
    }

    /**
     * Создание кнопки клавиатуры
     */
    private InlineKeyboardButton createButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }

    /**
     * Создание краткого отображения баланса для встраивания
     * 
     * @param balance баланс пользователя
     * @return краткая строка с балансом
     */
    public String formatBalanceBrief(SimpleBalanceResponse balance) {
        if (balance == null || !balance.isActive()) {
            return "❌ Баланс недоступен";
        }

        return String.format("%s Баланс: %s",
                BALANCE_EMOJI,
                balance.getFormattedBalance());
    }

    /**
     * Создание сообщения об ошибке получения баланса
     * 
     * @param userId       ID пользователя
     * @param errorMessage сообщение об ошибке
     * @return TelegramUIResponse с сообщением об ошибке
     */
    public TelegramUIResponse createErrorDisplay(Long userId, String errorMessage) {
        String messageText = String.format(
                "❌ <b>Ошибка получения баланса</b>\n\n" +
                        "Не удалось загрузить информацию о вашем балансе.\n" +
                        "<i>Причина: %s</i>\n\n" +
                        "Попробуйте позже или обратитесь в поддержку.",
                errorMessage != null ? errorMessage : "Неизвестная ошибка");

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        InlineKeyboardButton retryButton = createButton("🔄 Повторить", "retry_balance");
        keyboard.setKeyboard(List.of(List.of(retryButton)));

        return TelegramUIResponse.newMessage(userId, messageText)
                .keyboard(keyboard)
                .parseMode("HTML")
                .build();
    }
}