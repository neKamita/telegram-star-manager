package shit.back.telegram.ui.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import shit.back.entity.TransactionStatus;
import shit.back.domain.starPurchase.StarPurchaseAggregate;
import shit.back.telegram.ui.strategy.utils.StrategyConstants;

import java.util.List;

/**
 * Стратегия отображения истории покупок звезд
 * 
 * Форматирует историю покупок с указанием того, что бот покупает
 * звезды ЗА пользователя через Fragment API
 */
@Component
public class PurchaseHistoryStrategy implements TelegramMessageStrategy {

    private static final Logger logger = LoggerFactory.getLogger(PurchaseHistoryStrategy.class);

    private static final String STRATEGY_TYPE = "PURCHASE_HISTORY";
    private static final String[] SUPPORTED_TYPES = {
            "STAR_PURCHASE_HISTORY", "RECENT_PURCHASES", "PURCHASE_DETAILS"
    };

    // Используем константы из StrategyConstants

    @Override
    public String getStrategyType() {
        return STRATEGY_TYPE;
    }

    @Override
    public boolean canHandle(String contentType) {
        if (contentType == null)
            return false;
        for (String type : SUPPORTED_TYPES) {
            if (type.equals(contentType))
                return true;
        }
        return false;
    }

    @Override
    public String[] getSupportedContentTypes() {
        return SUPPORTED_TYPES.clone();
    }

    @Override
    public String formatContent(String contentType, Object data) {
        logger.debug("Форматирование контента типа: {}, данные: {}", contentType, data.getClass().getSimpleName());
        return switch (contentType) {
            case "STAR_PURCHASE_HISTORY" -> formatStarPurchaseHistory(data);
            case "RECENT_PURCHASES" -> formatRecentPurchases(data);
            case "PURCHASE_DETAILS" -> formatPurchaseDetails(data);
            default -> throw new IllegalArgumentException("Неподдерживаемый тип контента: " + contentType);
        };
    }

    /**
     * Форматирование полной истории покупок звезд
     */
    @SuppressWarnings("unchecked")
    private String formatStarPurchaseHistory(Object data) {
        if (!(data instanceof List<?> list)) {
            throw new IllegalArgumentException("Ожидался List<StarPurchaseAggregate> для STAR_PURCHASE_HISTORY");
        }

        List<StarPurchaseAggregate> purchases = (List<StarPurchaseAggregate>) list;

        if (purchases.isEmpty()) {
            return """
                    📋 <b>История покупок</b>

                    📭 <i>История покупок пуста</i>

                    💡 <b>Как это работает:</b>
                    • Вы пополняете баланс удобным способом
                    • Бот покупает звезды ЗА вас через Telegram
                    • Звезды зачисляются на ваш аккаунт

                    🚀 <i>Начните с пополнения баланса!</i>
                    """;
        }

        StringBuilder message = new StringBuilder();
        message.append("📋 <b>История покупок звезд</b>\n\n");

        // Статистика
        int totalPurchases = purchases.size();
        int successfulPurchases = (int) purchases.stream().filter(StarPurchaseAggregate::isCompleted).count();
        int totalStars = purchases.stream()
                .filter(StarPurchaseAggregate::isCompleted)
                .mapToInt(p -> p.getActualStarsReceived() != null ? p.getActualStarsReceived() : 0)
                .sum();

        message.append("📊 <b>Общая статистика:</b>\n");
        message.append(String.format("🛒 Всего покупок: %d\n", totalPurchases));
        message.append(String.format("✅ Успешных: %d\n", successfulPurchases));
        message.append(String.format("⭐ Получено звезд: %d\n\n", totalStars));

        // Последние покупки
        message.append("🕐 <b>Последние покупки:</b>\n");
        purchases.stream()
                .limit(StrategyConstants.MAX_HISTORY_ITEMS)
                .forEach(purchase -> message.append(formatSinglePurchase(purchase)));

        if (purchases.size() > StrategyConstants.MAX_HISTORY_ITEMS) {
            message.append(String.format("\n<i>... и еще %d покупок</i>",
                    purchases.size() - StrategyConstants.MAX_HISTORY_ITEMS));
        }

        message.append("\n💡 <i>Бот покупает звезды ЗА вас через Telegram Fragment</i>");

        return message.toString();
    }

    /**
     * Форматирование недавних покупок (краткий формат)
     */
    @SuppressWarnings("unchecked")
    private String formatRecentPurchases(Object data) {
        if (!(data instanceof List<?> list)) {
            throw new IllegalArgumentException("Ожидался List<StarPurchaseAggregate> для RECENT_PURCHASES");
        }

        List<StarPurchaseAggregate> purchases = (List<StarPurchaseAggregate>) list;

        if (purchases.isEmpty()) {
            return """
                    🕐 <b>Недавние покупки</b>

                    📭 <i>Недавних покупок нет</i>

                    💫 <i>Покупки звезд появятся здесь</i>
                    """;
        }

        StringBuilder message = new StringBuilder();
        message.append("🕐 <b>Недавние покупки</b>\n\n");

        purchases.stream()
                .limit(StrategyConstants.MAX_RECENT_PURCHASES)
                .forEach(purchase -> {
                    String statusIcon = getStatusIcon(purchase.getStatus());
                    String currencySymbol = purchase.getCurrency().getSymbol();

                    message.append(String.format("%s %s • %s %s • %s\n",
                            statusIcon,
                            purchase.isCompleted() ? String.format("⭐%d", purchase.getActualStarsReceived())
                                    : String.format("⭐%d", purchase.getRequestedStars()),
                            purchase.getPurchaseAmount().getFormattedAmount(),
                            currencySymbol,
                            purchase.getCreatedAt().format(StrategyConstants.DATE_FORMATTER)));
                });

        message.append("\n🤖 <i>Бот автоматически покупает звезды через Telegram</i>");

        return message.toString();
    }

    /**
     * Форматирование деталей одной покупки
     */
    private String formatPurchaseDetails(Object data) {
        if (!(data instanceof StarPurchaseAggregate purchase)) {
            throw new IllegalArgumentException("Ожидался StarPurchaseAggregate для PURCHASE_DETAILS");
        }

        StringBuilder message = new StringBuilder();
        String statusIcon = getStatusIcon(purchase.getStatus());
        String currencySymbol = purchase.getCurrency().getSymbol();

        message.append("🔍 <b>Детали покупки</b>\n\n");

        // Основная информация
        message.append(String.format("🆔 <b>ID:</b> <code>%s</code>\n",
                purchase.getStarPurchaseId().getShortValue()));
        message.append(String.format("%s <b>Статус:</b> %s\n",
                statusIcon, formatStatus(purchase.getStatus())));

        // Информация о звездах
        message.append(String.format("⭐ <b>Запрошено:</b> %d звезд\n",
                purchase.getRequestedStars()));
        if (purchase.isCompleted() && purchase.getActualStarsReceived() != null) {
            message.append(String.format("✅ <b>Получено:</b> %d звезд\n",
                    purchase.getActualStarsReceived()));
        }

        // Финансовая информация
        message.append(String.format("💰 <b>Сумма:</b> %s %s\n",
                purchase.getPurchaseAmount().getFormattedAmount(), currencySymbol));
        message.append(String.format("💱 <b>Валюта:</b> %s\n",
                purchase.getCurrency().getFormattedName()));

        // Временные метки
        message.append(String.format("🕐 <b>Создано:</b> %s\n",
                purchase.getCreatedAt().format(StrategyConstants.DATE_FORMATTER)));
        if (purchase.getCompletedAt() != null) {
            message.append(String.format("✅ <b>Завершено:</b> %s\n",
                    purchase.getCompletedAt().format(StrategyConstants.DATE_FORMATTER)));
        }

        // Fragment транзакция
        if (purchase.hasFragmentTransaction()) {
            message.append(String.format("🔗 <b>Fragment ID:</b> <code>%s</code>\n",
                    purchase.getFragmentTransactionId().getShortValue()));
        }

        // Описание или ошибка
        if (purchase.getDescription() != null) {
            message.append(String.format("📝 <b>Описание:</b> %s\n", purchase.getDescription()));
        }
        if (!purchase.isCompleted() && purchase.getErrorMessage() != null) {
            message.append(String.format("❌ <b>Ошибка:</b> %s\n", purchase.getErrorMessage()));
        }

        message.append("\n🤖 <i>Покупка выполнена ботом через Telegram Fragment API</i>");

        return message.toString();
    }

    /**
     * Форматирование одной покупки для списка
     */
    private String formatSinglePurchase(StarPurchaseAggregate purchase) {
        String statusIcon = getStatusIcon(purchase.getStatus());
        String currencySymbol = purchase.getCurrency().getSymbol();

        StringBuilder item = new StringBuilder();
        item.append(String.format("• %s ", statusIcon));

        if (purchase.isCompleted()) {
            item.append(String.format("⭐<b>%d</b> за %s %s",
                    purchase.getActualStarsReceived(),
                    purchase.getPurchaseAmount().getFormattedAmount(),
                    currencySymbol));
        } else {
            item.append(String.format("❌ ⭐%d за %s %s",
                    purchase.getRequestedStars(),
                    purchase.getPurchaseAmount().getFormattedAmount(),
                    currencySymbol));
        }

        item.append(String.format("\n   <i>%s</i>\n",
                purchase.getCreatedAt().format(StrategyConstants.DATE_FORMATTER)));

        if (!purchase.isCompleted() && purchase.getErrorMessage() != null) {
            item.append(String.format("   <i>%s</i>\n", purchase.getErrorMessage()));
        }

        return item.toString();
    }

    /**
     * Получение иконки статуса
     */
    private String getStatusIcon(TransactionStatus status) {
        return switch (status) {
            case PENDING -> "🔄";
            case COMPLETED -> "✅";
            case FAILED -> "❌";
            case CANCELLED -> "🚫";
            default -> "❓";
        };
    }

    /**
     * Форматирование статуса транзакции
     */
    private String formatStatus(TransactionStatus status) {
        return switch (status) {
            case PENDING -> "В обработке";
            case COMPLETED -> "Завершена";
            case FAILED -> "Неудачно";
            case CANCELLED -> "Отменена";
            default -> "Неизвестно";
        };
    }
}