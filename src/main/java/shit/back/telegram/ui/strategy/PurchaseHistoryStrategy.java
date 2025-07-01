package shit.back.telegram.ui.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import shit.back.entity.TransactionStatus;
import shit.back.domain.starPurchase.StarPurchaseAggregate;
import shit.back.application.balance.dto.response.StarPurchaseResponse;
import shit.back.domain.balance.valueobjects.Currency;
import shit.back.domain.balance.valueobjects.Money;
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
     * ИСПРАВЛЕНО: Теперь работает с List<StarPurchaseResponse> вместо
     * List<StarPurchaseAggregate>
     */
    @SuppressWarnings("unchecked")
    private String formatStarPurchaseHistory(Object data) {
        if (!(data instanceof List<?> list)) {
            throw new IllegalArgumentException("Ожидался List<StarPurchaseResponse> для STAR_PURCHASE_HISTORY");
        }

        // КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: Проверяем тип элементов списка
        if (!list.isEmpty() && !(list.get(0) instanceof StarPurchaseResponse)) {
            throw new IllegalArgumentException("Ожидался List<StarPurchaseResponse>, получен: " +
                    (list.get(0) != null ? list.get(0).getClass().getSimpleName() : "null"));
        }

        List<StarPurchaseResponse> purchases = (List<StarPurchaseResponse>) list;

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

        // ИСПРАВЛЕНО: Статистика для StarPurchaseResponse
        int totalPurchases = purchases.size();
        int successfulPurchases = (int) purchases.stream().filter(StarPurchaseResponse::isSuccessful).count();
        int totalStars = purchases.stream()
                .filter(StarPurchaseResponse::isSuccessful)
                .mapToInt(p -> p.getStarCount() != null ? p.getStarCount() : 0)
                .sum();

        message.append("📊 <b>Общая статистика:</b>\n");
        message.append(String.format("🛒 Всего покупок: %d\n", totalPurchases));
        message.append(String.format("✅ Успешных: %d\n", successfulPurchases));
        message.append(String.format("⭐ Получено звезд: %d\n\n", totalStars));

        // ИСПРАВЛЕНО: Последние покупки для StarPurchaseResponse
        message.append("🕐 <b>Последние покупки:</b>\n");
        purchases.stream()
                .limit(StrategyConstants.MAX_HISTORY_ITEMS)
                .forEach(purchase -> message.append(formatSinglePurchaseResponse(purchase)));

        if (purchases.size() > StrategyConstants.MAX_HISTORY_ITEMS) {
            message.append(String.format("\n<i>... и еще %d покупок</i>",
                    purchases.size() - StrategyConstants.MAX_HISTORY_ITEMS));
        }

        message.append("\n💡 <i>Бот покупает звезды ЗА вас через Telegram Fragment</i>");

        return message.toString();
    }

    /**
     * Форматирование недавних покупок (краткий формат)
     * ИСПРАВЛЕНО: Теперь работает с List<StarPurchaseResponse>
     */
    @SuppressWarnings("unchecked")
    private String formatRecentPurchases(Object data) {
        if (!(data instanceof List<?> list)) {
            throw new IllegalArgumentException("Ожидался List<StarPurchaseResponse> для RECENT_PURCHASES");
        }

        // ИСПРАВЛЕНО: Безопасная проверка типа элементов
        if (!list.isEmpty() && !(list.get(0) instanceof StarPurchaseResponse)) {
            throw new IllegalArgumentException("Ожидался List<StarPurchaseResponse>, получен: " +
                    (list.get(0) != null ? list.get(0).getClass().getSimpleName() : "null"));
        }

        List<StarPurchaseResponse> purchases = (List<StarPurchaseResponse>) list;

        if (purchases.isEmpty()) {
            return """
                    🕐 <b>Недавние покупки</b>

                    📭 <i>Недавних покупок нет</i>

                    💫 <i>Покупки звезд появятся здесь</i>
                    """;
        }

        StringBuilder message = new StringBuilder();
        message.append("🕐 <b>Недавние покупки</b>\n\n");

        // ИСПРАВЛЕНО: Форматирование для StarPurchaseResponse
        purchases.stream()
                .limit(StrategyConstants.MAX_RECENT_PURCHASES)
                .forEach(purchase -> {
                    String statusIcon = getStatusIconFromString(purchase.getStatus());
                    String currencySymbol = getCurrencySymbol(purchase.getAmount());

                    message.append(String.format("%s %s • %s %s • %s\n",
                            statusIcon,
                            purchase.isSuccessful() ? String.format("⭐%d", purchase.getStarCount())
                                    : String.format("⭐%d", purchase.getStarCount()),
                            getFormattedAmount(purchase.getAmount()),
                            currencySymbol,
                            purchase.getCreatedAt().format(StrategyConstants.DATE_FORMATTER)));
                });

        message.append("\n🤖 <i>Бот автоматически покупает звезды через Telegram</i>");

        return message.toString();
    }

    /**
     * Форматирование деталей одной покупки
     * ИСПРАВЛЕНО: Поддерживает как StarPurchaseAggregate, так и
     * StarPurchaseResponse
     */
    private String formatPurchaseDetails(Object data) {
        StringBuilder message = new StringBuilder();
        message.append("🔍 <b>Детали покупки</b>\n\n");

        if (data instanceof StarPurchaseAggregate purchase) {
            // Обработка StarPurchaseAggregate (старый код)
            String statusIcon = getStatusIcon(purchase.getStatus());
            String currencySymbol = purchase.getCurrency().getSymbol();

            message.append(String.format("🆔 <b>ID:</b> <code>%s</code>\n",
                    purchase.getStarPurchaseId().getShortValue()));
            message.append(String.format("%s <b>Статус:</b> %s\n",
                    statusIcon, formatStatus(purchase.getStatus())));

            message.append(String.format("⭐ <b>Запрошено:</b> %d звезд\n",
                    purchase.getRequestedStars()));
            if (purchase.isCompleted() && purchase.getActualStarsReceived() != null) {
                message.append(String.format("✅ <b>Получено:</b> %d звезд\n",
                        purchase.getActualStarsReceived()));
            }

            message.append(String.format("💰 <b>Сумма:</b> %s %s\n",
                    purchase.getPurchaseAmount().getFormattedAmount(), currencySymbol));
            message.append(String.format("💱 <b>Валюта:</b> %s\n",
                    purchase.getCurrency().getFormattedName()));

            message.append(String.format("🕐 <b>Создано:</b> %s\n",
                    purchase.getCreatedAt().format(StrategyConstants.DATE_FORMATTER)));
            if (purchase.getCompletedAt() != null) {
                message.append(String.format("✅ <b>Завершено:</b> %s\n",
                        purchase.getCompletedAt().format(StrategyConstants.DATE_FORMATTER)));
            }

            if (purchase.hasFragmentTransaction()) {
                message.append(String.format("🔗 <b>Fragment ID:</b> <code>%s</code>\n",
                        purchase.getFragmentTransactionId().getShortValue()));
            }

            if (purchase.getDescription() != null) {
                message.append(String.format("📝 <b>Описание:</b> %s\n", purchase.getDescription()));
            }
            if (!purchase.isCompleted() && purchase.getErrorMessage() != null) {
                message.append(String.format("❌ <b>Ошибка:</b> %s\n", purchase.getErrorMessage()));
            }

        } else if (data instanceof StarPurchaseResponse purchase) {
            // ИСПРАВЛЕНО: Обработка StarPurchaseResponse
            String statusIcon = getStatusIconFromString(purchase.getStatus());
            String currencySymbol = getCurrencySymbol(purchase.getAmount());

            message.append(String.format("🆔 <b>ID:</b> <code>%s</code>\n",
                    purchase.getTransactionId()));
            message.append(String.format("%s <b>Статус:</b> %s\n",
                    statusIcon, formatStatusFromString(purchase.getStatus())));

            message.append(String.format("⭐ <b>Звезды:</b> %d\n",
                    purchase.getStarCount()));

            message.append(String.format("💰 <b>Сумма:</b> %s %s\n",
                    getFormattedAmount(purchase.getAmount()), currencySymbol));

            message.append(String.format("🕐 <b>Создано:</b> %s\n",
                    purchase.getCreatedAt().format(StrategyConstants.DATE_FORMATTER)));
            if (purchase.getCompletedAt() != null) {
                message.append(String.format("✅ <b>Завершено:</b> %s\n",
                        purchase.getCompletedAt().format(StrategyConstants.DATE_FORMATTER)));
            }

            if (purchase.getErrorMessage() != null) {
                message.append(String.format("❌ <b>Ошибка:</b> %s\n", purchase.getErrorMessage()));
            }

        } else {
            throw new IllegalArgumentException(
                    "Ожидался StarPurchaseAggregate или StarPurchaseResponse для PURCHASE_DETAILS, получен: " +
                            (data != null ? data.getClass().getSimpleName() : "null"));
        }

        message.append("\n🤖 <i>Покупка выполнена ботом через Telegram Fragment API</i>");
        return message.toString();
    }

    /**
     * Форматирование одной покупки для списка (StarPurchaseAggregate)
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
     * НОВЫЙ МЕТОД: Форматирование одной покупки для списка (StarPurchaseResponse)
     */
    private String formatSinglePurchaseResponse(StarPurchaseResponse purchase) {
        String statusIcon = getStatusIconFromString(purchase.getStatus());
        String currencySymbol = getCurrencySymbol(purchase.getAmount());

        StringBuilder item = new StringBuilder();
        item.append(String.format("• %s ", statusIcon));

        if (purchase.isSuccessful()) {
            item.append(String.format("⭐<b>%d</b> за %s %s",
                    purchase.getStarCount(),
                    getFormattedAmount(purchase.getAmount()),
                    currencySymbol));
        } else {
            item.append(String.format("❌ ⭐%d за %s %s",
                    purchase.getStarCount(),
                    getFormattedAmount(purchase.getAmount()),
                    currencySymbol));
        }

        item.append(String.format("\n   <i>%s</i>\n",
                purchase.getCreatedAt().format(StrategyConstants.DATE_FORMATTER)));

        if (!purchase.isSuccessful() && purchase.getErrorMessage() != null) {
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

    /**
     * НОВЫЕ ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ для работы с StarPurchaseResponse
     */

    /**
     * Получение иконки статуса из строки
     */
    private String getStatusIconFromString(String status) {
        if (status == null)
            return "❓";
        return switch (status.toUpperCase()) {
            case "PENDING", "PROCESSING" -> "🔄";
            case "COMPLETED", "SUCCESS" -> "✅";
            case "FAILED", "ERROR" -> "❌";
            case "CANCELLED" -> "🚫";
            default -> "❓";
        };
    }

    /**
     * Форматирование статуса из строки
     */
    private String formatStatusFromString(String status) {
        if (status == null)
            return "Неизвестно";
        return switch (status.toUpperCase()) {
            case "PENDING", "PROCESSING" -> "В обработке";
            case "COMPLETED", "SUCCESS" -> "Завершена";
            case "FAILED", "ERROR" -> "Неудачно";
            case "CANCELLED" -> "Отменена";
            default -> "Неизвестно";
        };
    }

    /**
     * Получение символа валюты из Money объекта
     */
    private String getCurrencySymbol(Money money) {
        if (money == null)
            return "$";
        // Предполагаем, что у Money есть метод для получения валюты
        // Если нет, возвращаем дефолтный символ
        return "$"; // TODO: Реализовать получение символа валюты из Money
    }

    /**
     * Получение отформатированной суммы из Money объекта
     */
    private String getFormattedAmount(Money money) {
        if (money == null)
            return "0.00";
        // Предполагаем, что у Money есть метод для форматирования
        // Если нет, используем toString или другой доступный метод
        return money.toString(); // TODO: Реализовать правильное форматирование Money
    }
}