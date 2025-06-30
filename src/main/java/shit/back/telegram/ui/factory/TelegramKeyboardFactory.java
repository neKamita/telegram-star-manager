package shit.back.telegram.ui.factory;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import shit.back.telegram.ui.builder.TelegramKeyboardBuilder;

/**
 * Factory для создания стандартных Telegram клавиатур
 * 
 * Мигрирована из presentation layer для новой архитектуры
 */
@Component
public class TelegramKeyboardFactory {

    /**
     * Создать главное меню
     */
    public InlineKeyboardMarkup createMainMenu() {
        return new TelegramKeyboardBuilder()
                .addButton("💰 Баланс", "balance_info")
                .addButton("⭐ Купить Stars", "buy_stars")
                .newRow()
                .addButton("📊 История", "balance_history")
                .addButton("❓ Помощь", "help")
                .build();
    }

    /**
     * Создать меню баланса
     */
    public InlineKeyboardMarkup createBalanceMenu() {
        return new TelegramKeyboardBuilder()
                .addButton("💳 Пополнить", "topup_balance")
                .addButton("📊 История", "balance_history")
                .newRow()
                .addButton("🏠 Главное меню", "main_menu")
                .build();
    }

    /**
     * Создать клавиатуру пополнения
     */
    public InlineKeyboardMarkup createTopupKeyboard() {
        return new TelegramKeyboardBuilder()
                .addButton("💵 100₽", "topup_100")
                .addButton("💵 500₽", "topup_500")
                .newRow()
                .addButton("💵 1000₽", "topup_1000")
                .addButton("✏️ Своя сумма", "topup_custom")
                .newRow()
                .addButton("🏠 Главное меню", "main_menu")
                .build();
    }

    /**
     * Создать клавиатуру с возвратом в главное меню
     */
    public InlineKeyboardMarkup createBackToMain() {
        return new TelegramKeyboardBuilder()
                .addButton("🏠 Главное меню", "main_menu")
                .build();
    }

    /**
     * Создать клавиатуру помощи
     */
    public InlineKeyboardMarkup createHelpMenu() {
        return new TelegramKeyboardBuilder()
                .addButton("💰 О балансе", "help_balance")
                .addButton("⭐ О Stars", "help_stars")
                .newRow()
                .addButton("💳 Способы оплаты", "help_payment")
                .addButton("📞 Поддержка", "help_support")
                .newRow()
                .addButton("🏠 Главное меню", "main_menu")
                .build();
    }
}