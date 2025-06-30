package shit.back.telegram.queries.handlers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import shit.back.application.balance.dto.response.StarPurchaseResponse;
import shit.back.telegram.dto.TelegramResponse;
import shit.back.telegram.queries.ShowPurchaseHistoryQuery;
import shit.back.telegram.queries.TelegramQueryHandler;
import shit.back.telegram.ui.strategy.PurchaseHistoryStrategy;
import shit.back.telegram.ui.builder.TelegramKeyboardBuilder;

import java.util.Collections;
import java.util.List;

/**
 * Обработчик запроса истории покупок с пагинацией
 * 
 * Интегрируется с PurchaseHistoryStrategy для отображения истории
 */
@Component
@Slf4j
public class ShowPurchaseHistoryQueryHandler implements TelegramQueryHandler<ShowPurchaseHistoryQuery> {

    @Autowired
    private PurchaseHistoryStrategy purchaseHistoryStrategy;

    // TODO: Добавить реальный сервис для получения истории покупок
    // @Autowired
    // private StarPurchaseApplicationService starPurchaseService;

    @Override
    @Transactional(readOnly = true)
    public TelegramResponse handle(ShowPurchaseHistoryQuery query) throws Exception {
        log.info("📋 Запрос истории покупок: userId={}, page={}, limit={}, filter={}",
                query.getUserId(), query.getPage(), query.getLimit(), query.getFilterBy());

        try {
            // Валидация запроса
            query.validate();

            // Получаем историю покупок
            List<StarPurchaseResponse> purchaseHistory = getPurchaseHistory(query);

            // Форматируем сообщение через стратегию
            String contentType = query.getContentType();
            String formattedMessage = purchaseHistoryStrategy.formatContent(contentType, purchaseHistory);

            // Добавляем информацию о пагинации если нужно
            if (query.getPage() > 0 || purchaseHistory.size() == query.getLimit()) {
                formattedMessage = addPaginationInfo(formattedMessage, query, purchaseHistory.size());
            }

            // Создаем клавиатуру с навигацией и фильтрами
            var keyboardBuilder = new TelegramKeyboardBuilder();

            // Навигация по страницам
            if (query.getPage() > 0) {
                keyboardBuilder.addButton("◀️ Предыдущая", "history_page_" + (query.getPage() - 1));
            }

            if (purchaseHistory.size() == query.getLimit()) {
                keyboardBuilder.addButton("Следующая ▶️", "history_page_" + (query.getPage() + 1));
            }

            keyboardBuilder.newRow();

            // Фильтры
            if (!query.getFilterBy().equals("ALL")) {
                keyboardBuilder.addButton("🔍 Все", "history_filter_ALL");
            }
            if (!query.getFilterBy().equals("SUCCESSFUL")) {
                keyboardBuilder.addButton("✅ Успешные", "history_filter_SUCCESSFUL");
            }
            if (!query.getFilterBy().equals("FAILED")) {
                keyboardBuilder.addButton("❌ Неудачные", "history_filter_FAILED");
            }

            keyboardBuilder.newRow()
                    .addButton("🔄 Обновить", "refresh_history")
                    .addButton("🔙 Назад", "show_balance");

            var keyboard = keyboardBuilder.build();

            log.info("✅ История покупок успешно получена для пользователя: {} (найдено: {})",
                    query.getUserId(), purchaseHistory.size());

            return TelegramResponse.builder()
                    .successful(true)
                    .message(formattedMessage)
                    .uiType(contentType)
                    .uiData(purchaseHistory)
                    .data(keyboard)
                    .build();

        } catch (IllegalArgumentException e) {
            log.warn("❌ Некорректный запрос истории от пользователя {}: {}", query.getUserId(), e.getMessage());
            return TelegramResponse.error("❌ " + e.getMessage());

        } catch (Exception e) {
            log.error("❌ Ошибка при получении истории покупок для пользователя {}: {}",
                    query.getUserId(), e.getMessage(), e);
            return TelegramResponse.error("Не удалось получить историю покупок: " + e.getMessage());
        }
    }

    @Override
    public Class<ShowPurchaseHistoryQuery> getQueryType() {
        return ShowPurchaseHistoryQuery.class;
    }

    @Override
    public int getHandlerPriority() {
        return 30; // Нормальный приоритет для истории
    }

    @Override
    public boolean supportsCaching() {
        return true; // Поддерживаем кэширование истории
    }

    @Override
    public String getDescription() {
        return "Обработчик запросов истории покупок с интеграцией PurchaseHistoryStrategy";
    }

    /**
     * Получение истории покупок с учетом фильтров и пагинации
     */
    private List<StarPurchaseResponse> getPurchaseHistory(ShowPurchaseHistoryQuery query) {
        try {
            // TODO: Заменить на реальную логику получения истории
            // Временная заглушка - возвращаем пустой список
            log.info("📝 Получение истории покупок для пользователя {} (page={}, limit={}, filter={})",
                    query.getUserId(), query.getPage(), query.getLimit(), query.getFilterBy());

            // В реальной реализации здесь будет:
            // return starPurchaseService.getHistory(
            // query.getUserId(),
            // query.getOffset(),
            // query.getLimit(),
            // query.getStatusFilter()
            // );

            return Collections.emptyList();

        } catch (Exception e) {
            log.error("❌ Ошибка получения истории покупок: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Добавление информации о пагинации
     */
    private String addPaginationInfo(String baseMessage, ShowPurchaseHistoryQuery query, int resultsCount) {
        StringBuilder enhanced = new StringBuilder(baseMessage);

        enhanced.append("\n\n📄 <b>Навигация:</b>\n");

        if (query.getPage() > 0) {
            enhanced.append(String.format("◀️ Страница %d (показано: %d)\n",
                    query.getPage() + 1, resultsCount));
        } else {
            enhanced.append(String.format("📍 Первая страница (показано: %d)\n", resultsCount));
        }

        if (resultsCount == query.getLimit()) {
            enhanced.append("▶️ Есть еще записи\n");
        }

        // Информация о фильтрах
        if (!query.getFilterBy().equals("ALL")) {
            enhanced.append(String.format("🔍 Фильтр: %s\n", formatFilterName(query.getFilterBy())));
        }

        return enhanced.toString();
    }

    /**
     * Форматирование названия фильтра
     */
    private String formatFilterName(String filterBy) {
        return switch (filterBy) {
            case "RECENT" -> "Недавние";
            case "SUCCESSFUL" -> "Успешные";
            case "FAILED" -> "Неудачные";
            case "BY_STATUS" -> "По статусу";
            default -> filterBy;
        };
    }
}