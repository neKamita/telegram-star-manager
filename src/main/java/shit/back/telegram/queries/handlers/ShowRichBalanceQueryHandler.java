package shit.back.telegram.queries.handlers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import shit.back.application.balance.dto.response.SimpleBalanceResponse;
import shit.back.application.balance.service.BalanceApplicationFacade;
import shit.back.telegram.dto.TelegramResponse;
import shit.back.telegram.queries.ShowRichBalanceQuery;
import shit.back.telegram.queries.TelegramQueryHandler;
import shit.back.telegram.ui.strategy.BalanceDisplayStrategy;
import shit.back.telegram.ui.builder.TelegramKeyboardBuilder;

/**
 * Обработчик расширенного запроса баланса с UI опциями
 * 
 * Интегрируется с BalanceDisplayStrategy для создания богатого UI
 */
@Component
@Slf4j
public class ShowRichBalanceQueryHandler implements TelegramQueryHandler<ShowRichBalanceQuery> {

    @Autowired
    private BalanceApplicationFacade balanceApplicationFacade;

    @Autowired
    private BalanceDisplayStrategy balanceDisplayStrategy;

    @Override
    @Transactional(readOnly = true)
    public TelegramResponse handle(ShowRichBalanceQuery query) throws Exception {
        log.info("💰 Расширенный запрос баланса: userId={}, format={}, history={}, stats={}",
                query.getUserId(), query.getDisplayFormat(), query.isIncludeHistory(), query.isIncludeStatistics());

        try {
            // Валидация запроса
            query.validate();

            // Получаем данные баланса
            var balanceResult = balanceApplicationFacade.getBalance(query.getUserId());

            if (!balanceResult.isSuccess()) {
                String error = "Не удалось получить информацию о балансе";
                log.error("❌ {} для пользователя {}", error, query.getUserId());
                return TelegramResponse.error(error);
            }

            // Получаем SimpleBalanceResponse из результата
            SimpleBalanceResponse balanceData = extractBalanceData(balanceResult);
            if (balanceData == null) {
                String error = "Некорректные данные баланса";
                log.error("❌ {} для пользователя {}", error, query.getUserId());
                return TelegramResponse.error(error);
            }

            // Форматируем сообщение через стратегию
            String formattedMessage = balanceDisplayStrategy.formatContent(query.getDisplayFormat(), balanceData);

            // Добавляем дополнительную информацию если запрошена
            if (query.isIncludeHistory() || query.isIncludeStatistics()) {
                formattedMessage = enhanceMessage(formattedMessage, query, balanceData);
            }

            // Создаем клавиатуру с действиями для баланса
            var keyboard = new TelegramKeyboardBuilder()
                    .addButton("⭐ Купить звезды", "buy_stars")
                    .newRow()
                    .addButton("📋 История операций", "show_history")
                    .addButton("💳 Пополнить", "topup_balance")
                    .newRow()
                    .addButton("🔄 Обновить", "refresh_balance")
                    .build();

            log.info("✅ Расширенный баланс успешно получен для пользователя: {}", query.getUserId());

            return TelegramResponse.builder()
                    .successful(true)
                    .message(formattedMessage)
                    .uiType(query.getDisplayFormat())
                    .uiData(balanceData)
                    .data(keyboard)
                    .build();

        } catch (IllegalArgumentException e) {
            log.warn("❌ Некорректный запрос от пользователя {}: {}", query.getUserId(), e.getMessage());
            return TelegramResponse.error("❌ " + e.getMessage());

        } catch (Exception e) {
            log.error("❌ Ошибка при получении расширенного баланса для пользователя {}: {}",
                    query.getUserId(), e.getMessage(), e);
            return TelegramResponse.error("Не удалось получить информацию о балансе: " + e.getMessage());
        }
    }

    @Override
    public Class<ShowRichBalanceQuery> getQueryType() {
        return ShowRichBalanceQuery.class;
    }

    @Override
    public int getHandlerPriority() {
        return 10; // Очень высокий приоритет для расширенного баланса
    }

    @Override
    public boolean supportsCaching() {
        return true; // Поддерживаем кэширование
    }

    @Override
    public String getDescription() {
        return "Обработчик расширенных запросов баланса с интеграцией BalanceDisplayStrategy";
    }

    /**
     * Извлечение данных баланса из результата
     */
    private SimpleBalanceResponse extractBalanceData(Object balanceResult) {
        try {
            // Попытка получить SimpleBalanceResponse напрямую
            if (balanceResult instanceof SimpleBalanceResponse) {
                return (SimpleBalanceResponse) balanceResult;
            }

            // TODO: Здесь может потребоваться маппинг других типов результатов
            log.warn("⚠️ Неожиданный тип результата баланса: {}",
                    balanceResult != null ? balanceResult.getClass().getSimpleName() : "null");
            return null;

        } catch (Exception e) {
            log.error("❌ Ошибка извлечения данных баланса: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Дополнение сообщения историей и статистикой
     */
    private String enhanceMessage(String baseMessage, ShowRichBalanceQuery query, SimpleBalanceResponse balanceData) {
        StringBuilder enhanced = new StringBuilder(baseMessage);

        if (query.isIncludeHistory()) {
            enhanced.append("\n\n📈 <b>История операций:</b>\n");
            enhanced.append("• Последние переводы средств\n");
            enhanced.append("• Покупки звезд\n");
            enhanced.append("• Пополнения баланса\n");
            // TODO: Интеграция с реальной историей операций
        }

        if (query.isIncludeStatistics()) {
            enhanced.append("\n\n📊 <b>Статистика:</b>\n");
            enhanced.append(String.format("• Текущий баланс: %s\n",
                    balanceData.getFormattedBalance()));
            enhanced.append("• Статус: ").append(balanceData.isActive() ? "Активен" : "Неактивен").append("\n");
            enhanced.append("• Последнее обновление: ").append(
                    balanceData.getFormattedLastUpdated());
        }

        return enhanced.toString();
    }
}