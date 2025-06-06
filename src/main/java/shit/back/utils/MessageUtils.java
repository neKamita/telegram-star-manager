package shit.back.utils;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import shit.back.model.StarPackage;
import shit.back.model.Order;

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
    
    public static EditMessageText createEditMessageWithKeyboard(Long chatId, Integer messageId, String text, InlineKeyboardMarkup keyboard) {
        EditMessageText editMessage = createEditMessage(chatId, messageId, text);
        editMessage.setReplyMarkup(keyboard);
        return editMessage;
    }
    
    public static InlineKeyboardMarkup createMainMenuKeyboard() {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Первая строка
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createButton("💰 Купить звезды", "buy_stars"));
        row1.add(createButton("📊 Тарифы", "show_prices"));
        rows.add(row1);
        
        // Вторая строка
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createButton("📋 Мои заказы", "my_orders"));
        row2.add(createButton("❓ Помощь", "help"));
        rows.add(row2);
        
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
                pkg.getDiscountPercent()
            ));
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
            order.getStarPackage().getDiscountPercent()
        );
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
            order.getAmount()
        );
    }
    
    public static String formatHelpMessage() {
        return """
            ❓ <b>Помощь по использованию бота</b>
            
            🔹 <b>/start</b> - главное меню
            🔹 <b>/help</b> - эта справка
            🔹 <b>/prices</b> - показать тарифы
            🔹 <b>/status</b> - статус заказа
            
            💡 <b>Как купить звезды:</b>
            1️⃣ Нажмите "💰 Купить звезды"
            2️⃣ Выберите нужный пакет
            3️⃣ Подтвердите заказ
            4️⃣ Выберите способ оплаты
            5️⃣ Произведите оплату
            6️⃣ Получите звезды!
            
            📞 <b>Поддержка:</b> @support_bot
            """;
    }
}
