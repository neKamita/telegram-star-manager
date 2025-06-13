package shit.back.utils;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import shit.back.entity.BalanceTransactionEntity;
import shit.back.entity.UserBalanceEntity;
import shit.back.model.StarPackage;
import shit.back.model.Order;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class MessageUtils {

    public static SendMessage createMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setParseMode("HTML");
        return message;
    }

    public static SendMessage createMessageWithKeyboard(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage message = createMessage(chatId, text);
        message.setReplyMarkup(keyboard);
        return message;
    }

    public static EditMessageText createEditMessage(Long chatId, Integer messageId, String text) {
        EditMessageText editMessage = new EditMessageText();
        editMessage.setChatId(String.valueOf(chatId));
        editMessage.setMessageId(messageId);
        editMessage.setText(text);
        editMessage.setParseMode("HTML");
        return editMessage;
    }

    public static EditMessageText createEditMessageWithKeyboard(Long chatId, Integer messageId, String text,
            InlineKeyboardMarkup keyboard) {
        EditMessageText editMessage = createEditMessage(chatId, messageId, text);
        editMessage.setReplyMarkup(keyboard);
        return editMessage;
    }

    public static InlineKeyboardMarkup createMainMenuKeyboard() {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Первая строка - покупки
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createButton("⭐ Купить звезды", "buy_stars"));
        row1.add(createButton("📊 Тарифы", "show_prices"));
        rows.add(row1);

        // Вторая строка - баланс
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createButton("💰 Мой баланс", "show_balance"));
        row2.add(createButton("📈 История", "show_balance_history"));
        rows.add(row2);

        // Третья строка - управление
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(createButton("📋 Мои заказы", "my_orders"));
        row3.add(createButton("❓ Помощь", "help"));
        rows.add(row3);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    public static InlineKeyboardMarkup createPackageSelectionKeyboard(List<StarPackage> packages) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (StarPackage pkg : packages) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            String buttonText = String.format("⭐ %d - $%.2f", pkg.getStars(), pkg.getDiscountedPrice());
            row.add(createButton(buttonText, "select_package_" + pkg.getPackageId()));
            rows.add(row);
        }

        // Кнопка назад
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        backRow.add(createButton("🔙 Назад", "back_to_main"));
        rows.add(backRow);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    public static InlineKeyboardMarkup createOrderConfirmationKeyboard(String orderId) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Первая строка
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createButton("✅ Подтвердить заказ", "confirm_order_" + orderId));
        rows.add(row1);

        // Вторая строка
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createButton("🔙 Назад к тарифам", "buy_stars"));
        row2.add(createButton("❌ Отмена", "cancel_order"));
        rows.add(row2);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    public static InlineKeyboardMarkup createPaymentKeyboard(String orderId) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Способы оплаты (заглушки для будущего)
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createButton("💎 TON Wallet", "pay_ton_" + orderId));
        rows.add(row1);

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createButton("₿ Crypto", "pay_crypto_" + orderId));
        rows.add(row2);

        // Проверить платеж
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(createButton("🔄 Проверить платеж", "check_payment_" + orderId));
        rows.add(row3);

        // Отмена
        List<InlineKeyboardButton> row4 = new ArrayList<>();
        row4.add(createButton("❌ Отменить заказ", "cancel_order_" + orderId));
        rows.add(row4);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    /**
     * Создание расширенной клавиатуры оплаты с учетом баланса
     */
    public static InlineKeyboardMarkup createPaymentKeyboardWithBalance(String orderId, BigDecimal orderAmount,
            BigDecimal userBalance) {
        return createPaymentMethodKeyboard(orderId, orderAmount, userBalance);
    }

    public static InlineKeyboardMarkup createBackToMainKeyboard() {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(createButton("🔙 В главное меню", "back_to_main"));
        rows.add(row);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    public static InlineKeyboardMarkup createMyOrdersKeyboard() {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Кнопка "Создать новый заказ"
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createButton("💰 Новый заказ", "buy_stars"));
        rows.add(row1);

        // Кнопка "Назад"
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createButton("🔙 В главное меню", "back_to_main"));
        rows.add(row2);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    public static InlineKeyboardMarkup createHelpKeyboard() {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Полезные кнопки
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createButton("💰 Купить звезды", "buy_stars"));
        row1.add(createButton("📊 Тарифы", "show_prices"));
        rows.add(row1);

        // Кнопка "Назад"
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createButton("🔙 В главное меню", "back_to_main"));
        rows.add(row2);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    private static InlineKeyboardButton createButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }

    public static String formatWelcomeMessage(String userName) {
        return String.format("""
                🌟 <b>Добро пожаловать в StarBot, %s!</b>

                💫 Покупайте Telegram Stars с выгодой до <b>15%%</b>!

                🎯 <b>Наши преимущества:</b>
                • Низкие цены - экономьте на каждой покупке
                • Быстрая доставка звезд на ваш аккаунт
                • Безопасные криптоплатежи
                • Поддержка 24/7

                👇 Выберите действие:
                """, userName);
    }

    public static String formatPricesMessage(List<StarPackage> packages) {
        StringBuilder sb = new StringBuilder();
        sb.append("💰 <b>Наши тарифы на Telegram Stars:</b>\n\n");

        for (StarPackage pkg : packages) {
            sb.append(String.format(
                    "⭐ <b>%d звезд</b>\n" +
                            "💵 Цена: <b>$%.2f</b> <s>$%.2f</s>\n" +
                            "💸 Экономия: <b>$%.2f (%d%%)</b>\n\n",
                    pkg.getStars(),
                    pkg.getDiscountedPrice(),
                    pkg.getOriginalPrice(),
                    pkg.getSavings(),
                    pkg.getDiscountPercent()));
        }

        sb.append("🎁 <i>Чем больше покупаете - тем больше экономите!</i>");
        return sb.toString();
    }

    public static String formatOrderConfirmation(Order order) {
        return String.format("""
                📋 <b>Подтверждение заказа %s</b>

                ⭐ Количество звезд: <b>%d</b>
                💰 К оплате: <b>$%.2f</b>
                💸 Экономия: <b>$%.2f</b>

                🔥 <i>Вы экономите %d%% по сравнению с официальной ценой!</i>

                Подтвердить заказ?
                """,
                order.getFormattedOrderId(),
                order.getStarPackage().getStars(),
                order.getAmount(),
                order.getStarPackage().getSavings(),
                order.getStarPackage().getDiscountPercent());
    }

    public static String formatPaymentMessage(Order order) {
        return String.format("""
                💳 <b>Оплата заказа %s</b>

                ⭐ Звезды: <b>%d</b>
                💰 Сумма: <b>$%.2f</b>

                🔒 Выберите способ оплаты:

                <i>⚠️ После оплаты нажмите "Проверить платеж"</i>
                """,
                order.getFormattedOrderId(),
                order.getStarPackage().getStars(),
                order.getAmount());
    }

    public static String formatHelpMessage() {
        return """
                ❓ <b>Помощь по использованию бота</b>

                🎯 <b>Основные команды:</b>
                🔹 <b>/start</b> - главное меню
                🔹 <b>/help</b> - эта справка
                🔹 <b>/prices</b> - показать тарифы
                🔹 <b>/status</b> - статус заказа

                💰 <b>Команды баланса:</b>
                🔹 <b>/balance</b> - показать баланс
                🔹 <b>/topup</b> - пополнить баланс
                🔹 <b>/history</b> - история транзакций

                💡 <b>Как купить звезды:</b>
                1️⃣ Нажмите "⭐ Купить звезды"
                2️⃣ Выберите нужный пакет
                3️⃣ Подтвердите заказ
                4️⃣ Выберите способ оплаты (баланс или внешний)
                5️⃣ Произведите оплату
                6️⃣ Получите звезды!

                🏦 <b>Преимущества баланса:</b>
                • Мгновенная оплата без ввода карты
                • Безопасное хранение средств
                • История всех операций
                • Быстрые повторные покупки

                📞 <b>Поддержка:</b> @support_bot
                """;
    }

    // ============================================
    // === НОВЫЕ МЕТОДЫ ДЛЯ СИСТЕМЫ БАЛАНСА ===
    // ============================================

    /**
     * Создание сообщения с информацией о балансе пользователя
     */
    public static String createBalanceInfoMessage(UserBalanceEntity balance, String userName) {
        return String.format("""
                💰 <b>Баланс пользователя %s</b>

                💵 <b>Текущий баланс:</b> %.2f ₽
                📈 <b>Всего пополнено:</b> %.2f ₽
                📉 <b>Всего потрачено:</b> %.2f ₽
                🔄 <b>Общий оборот:</b> %.2f ₽
                💱 <b>Валюта:</b> %s

                📅 <b>Последнее обновление:</b> %s

                💡 <i>Используйте баланс для быстрых покупок без ввода платежных данных!</i>
                """,
                userName,
                balance.getCurrentBalance(),
                balance.getTotalDeposited(),
                balance.getTotalSpent(),
                balance.getTotalTurnover(),
                balance.getCurrency(),
                balance.getLastUpdated().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
    }

    /**
     * Создание сообщения для пополнения баланса
     */
    public static String createTopupMessage(UserBalanceEntity balance) {
        return String.format("""
                💳 <b>Пополнение баланса</b>

                💰 <b>Текущий баланс:</b> %.2f ₽

                💡 <b>Выберите сумму для пополнения:</b>

                🎯 <i>Рекомендуем пополнить баланс заранее для быстрых покупок!</i>
                """,
                balance.getCurrentBalance());
    }

    /**
     * Форматирование истории транзакций баланса
     */
    public static String createBalanceHistoryMessage(List<BalanceTransactionEntity> transactions, String userName) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("📊 <b>История транзакций %s</b>\n\n", userName));

        if (transactions.isEmpty()) {
            sb.append("📭 <i>У вас пока нет транзакций.</i>\n\n");
            sb.append("💡 Пополните баланс для удобных покупок!");
        } else {
            sb.append(String.format("📈 <b>Показано последних %d операций:</b>\n\n", transactions.size()));

            for (BalanceTransactionEntity transaction : transactions) {
                String typeIcon = getTransactionTypeIcon(transaction.getType());
                String statusIcon = getTransactionStatusIcon(transaction.getStatus());

                sb.append(String.format(
                        "%s %s <b>%.2f ₽</b>\n" +
                                "🔸 %s\n" +
                                "📅 %s\n" +
                                "🆔 <code>%s</code>\n\n",
                        typeIcon, statusIcon,
                        transaction.getAmount(),
                        transaction.getDescription(),
                        transaction.getCreatedAt().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")),
                        transaction.getTransactionId()));
            }

            sb.append("💡 <i>Для просмотра полной истории используйте веб-панель</i>");
        }

        return sb.toString();
    }

    /**
     * Создание клавиатуры главного меню баланса
     */
    public static InlineKeyboardMarkup createBalanceMenuKeyboard() {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Первая строка - основные действия
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createButton("💳 Пополнить", "topup_balance_menu"));
        row1.add(createButton("📊 История", "show_balance_history"));
        rows.add(row1);

        // Вторая строка - покупки
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createButton("⭐ Купить звезды", "buy_stars"));
        rows.add(row2);

        // Третья строка - возврат
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(createButton("🔙 В главное меню", "back_to_main"));
        rows.add(row3);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    /**
     * Создание клавиатуры для пополнения баланса
     */
    public static InlineKeyboardMarkup createTopupKeyboard() {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Варианты сумм пополнения
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createButton("💸 100 ₽", "topup_balance_100"));
        row1.add(createButton("💰 500 ₽", "topup_balance_500"));
        rows.add(row1);

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createButton("💎 1000 ₽", "topup_balance_1000"));
        row2.add(createButton("🏆 2000 ₽", "topup_balance_2000"));
        rows.add(row2);

        // Пользовательская сумма (пока заглушка)
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(createButton("✏️ Другая сумма", "topup_balance_custom"));
        rows.add(row3);

        // Возврат
        List<InlineKeyboardButton> row4 = new ArrayList<>();
        row4.add(createButton("🔙 Назад к балансу", "back_to_balance"));
        rows.add(row4);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    /**
     * Создание клавиатуры для истории транзакций
     */
    public static InlineKeyboardMarkup createBalanceHistoryKeyboard() {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Действия с историей
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createButton("🔄 Обновить", "refresh_balance_history"));
        row1.add(createButton("📄 Экспорт", "export_balance_history"));
        rows.add(row1);

        // Возврат
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createButton("🔙 Назад к балансу", "back_to_balance"));
        rows.add(row2);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    /**
     * Создание клавиатуры выбора способа оплаты
     */
    public static InlineKeyboardMarkup createPaymentMethodKeyboard(String orderId, BigDecimal amount,
            BigDecimal userBalance) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Оплата балансом (если достаточно средств)
        if (userBalance.compareTo(amount) >= 0) {
            List<InlineKeyboardButton> row1 = new ArrayList<>();
            row1.add(createButton(String.format("💰 Оплатить балансом (%.2f ₽)", userBalance),
                    "balance_payment_" + orderId));
            rows.add(row1);
        }

        // Внешние способы оплаты
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createButton("💎 TON Wallet", "pay_ton_" + orderId));
        row2.add(createButton("₿ Crypto", "pay_crypto_" + orderId));
        rows.add(row2);

        // Смешанная оплата (если баланса недостаточно но есть частичная сумма)
        if (userBalance.compareTo(BigDecimal.ZERO) > 0 && userBalance.compareTo(amount) < 0) {
            List<InlineKeyboardButton> row3 = new ArrayList<>();
            BigDecimal remaining = amount.subtract(userBalance);
            row3.add(createButton(String.format("🔄 Смешанная оплата (%.2f ₽ + %.2f ₽)",
                    userBalance, remaining), "mixed_payment_" + orderId));
            rows.add(row3);
        }

        // Отмена
        List<InlineKeyboardButton> row4 = new ArrayList<>();
        row4.add(createButton("❌ Отменить", "cancel_order_" + orderId));
        rows.add(row4);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    /**
     * Получение иконки для типа транзакции
     */
    private static String getTransactionTypeIcon(shit.back.entity.TransactionType type) {
        return switch (type) {
            case DEPOSIT -> "📥";
            case WITHDRAWAL -> "📤";
            case PURCHASE -> "🛒";
            case REFUND -> "↩️";
            case ADJUSTMENT -> "⚖️";
        };
    }

    /**
     * Получение иконки для статуса транзакции
     */
    private static String getTransactionStatusIcon(shit.back.entity.TransactionStatus status) {
        return switch (status) {
            case PENDING -> "⏳";
            case COMPLETED -> "✅";
            case CANCELLED -> "❌";
            case FAILED -> "💥";
        };
    }

}
