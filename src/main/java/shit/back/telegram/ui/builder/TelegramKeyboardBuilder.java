package shit.back.telegram.ui.builder;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder для создания Telegram клавиатур
 * 
 * Мигрирован из presentation layer для использования в новой архитектуре
 */
public class TelegramKeyboardBuilder {

    private final List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
    private List<InlineKeyboardButton> currentRow = new ArrayList<>();

    /**
     * Добавить кнопку в текущий ряд
     */
    public TelegramKeyboardBuilder addButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        currentRow.add(button);
        return this;
    }

    /**
     * Добавить кнопку с URL
     */
    public TelegramKeyboardBuilder addUrlButton(String text, String url) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setUrl(url);
        currentRow.add(button);
        return this;
    }

    /**
     * Перейти к новому ряду кнопок
     */
    public TelegramKeyboardBuilder newRow() {
        if (!currentRow.isEmpty()) {
            keyboard.add(new ArrayList<>(currentRow));
            currentRow.clear();
        }
        return this;
    }

    /**
     * Создать клавиатуру
     */
    public InlineKeyboardMarkup build() {
        // Добавляем последний ряд, если он не пустой
        if (!currentRow.isEmpty()) {
            keyboard.add(new ArrayList<>(currentRow));
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);
        return markup;
    }

    /**
     * Быстрое создание клавиатуры с одной кнопкой
     */
    public static InlineKeyboardMarkup singleButton(String text, String callbackData) {
        return new TelegramKeyboardBuilder()
                .addButton(text, callbackData)
                .build();
    }

    /**
     * Быстрое создание клавиатуры с кнопками в один ряд
     */
    public static InlineKeyboardMarkup singleRow(String... buttonsData) {
        TelegramKeyboardBuilder builder = new TelegramKeyboardBuilder();
        for (int i = 0; i < buttonsData.length; i += 2) {
            if (i + 1 < buttonsData.length) {
                builder.addButton(buttonsData[i], buttonsData[i + 1]);
            }
        }
        return builder.build();
    }
}