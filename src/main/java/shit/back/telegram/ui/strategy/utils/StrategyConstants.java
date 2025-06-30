package shit.back.telegram.ui.strategy.utils;

import java.time.format.DateTimeFormatter;

/**
 * Общие константы для стратегий UI
 * 
 * Централизует константы, используемые в различных стратегиях
 */
public final class StrategyConstants {

    private StrategyConstants() {
        // Утилитарный класс - запрещаем создание экземпляров
    }

    /**
     * Форматтер для отображения дат в пользовательском интерфейсе
     */
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    /**
     * Максимальное количество элементов истории для отображения
     */
    public static final int MAX_HISTORY_ITEMS = 10;

    /**
     * Максимальное количество недавних покупок для отображения
     */
    public static final int MAX_RECENT_PURCHASES = 5;
}