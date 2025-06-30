package shit.back.telegram.queries.handlers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import shit.back.application.balance.dto.response.DualBalanceResponse;
import shit.back.application.balance.service.BalanceApplicationFacade;
import shit.back.application.balance.mapper.BalanceResponseMapper;
import shit.back.domain.balance.valueobjects.Currency;
import shit.back.domain.balance.valueobjects.Money;
import shit.back.domain.dualBalance.valueobjects.DualBalanceId;
import shit.back.telegram.dto.TelegramResponse;
import shit.back.telegram.queries.ShowBalanceQuery;
import shit.back.telegram.queries.TelegramQueryHandler;
import shit.back.telegram.ui.strategy.BalanceDisplayStrategy;
import shit.back.telegram.ui.builder.TelegramKeyboardBuilder;

/**
 * Обработчик запроса просмотра баланса
 * 
 * Мигрирован из application.telegram.handlers
 */
@Component
@Slf4j
public class ShowBalanceQueryHandler implements TelegramQueryHandler<ShowBalanceQuery> {

    @Autowired
    private BalanceApplicationFacade balanceApplicationFacade;

    @Autowired
    private BalanceDisplayStrategy balanceDisplayStrategy;

    @Override
    @Transactional(readOnly = true)
    public TelegramResponse handle(ShowBalanceQuery query) throws Exception {
        log.info("💰 Запрос баланса: userId={}, includeHistory={}, includeStatistics={}",
                query.getUserId(), query.isIncludeHistory(), query.isIncludeStatistics());

        try {
            // Валидация запроса
            query.validate();

            // Получаем основную информацию о балансе
            var balanceResult = balanceApplicationFacade.getBalance(query.getUserId());

            if (!balanceResult.isSuccess()) {
                String error = "Не удалось получить информацию о балансе";
                log.error("❌ {} для пользователя {}", error, query.getUserId());
                return TelegramResponse.error(error);
            }

            // Получаем DualBalanceResponse из результата
            DualBalanceResponse balanceData = extractBalanceData(balanceResult, query.getUserId());
            if (balanceData == null) {
                String error = "Некорректные данные баланса";
                log.error("❌ {} для пользователя {}", error, query.getUserId());
                return TelegramResponse.error(error);
            }

            // Используем стратегию для форматирования
            String contentType = determineContentType(query);
            String formattedMessage = balanceDisplayStrategy.formatContent(contentType, balanceData);

            // Создаем клавиатуру действий
            var keyboard = new TelegramKeyboardBuilder()
                    .addButton("⭐ Купить звезды", "buy_stars")
                    .addButton("💸 Перевести средства", "transfer_funds")
                    .newRow()
                    .addButton("📋 История", "show_history")
                    .addButton("💳 Пополнить", "topup_balance")
                    .newRow()
                    .addButton("🔄 Обновить", "refresh_balance")
                    .build();

            log.info("✅ Баланс успешно получен для пользователя: {}", query.getUserId());

            return TelegramResponse.builder()
                    .successful(true)
                    .message(formattedMessage)
                    .uiType(contentType)
                    .uiData(balanceData)
                    .data(keyboard)
                    .build();

        } catch (Exception e) {
            log.error("❌ Ошибка при получении баланса для пользователя {}: {}",
                    query.getUserId(), e.getMessage(), e);
            return TelegramResponse.error("Не удалось получить информацию о балансе: " + e.getMessage());
        }
    }

    @Override
    public Class<ShowBalanceQuery> getQueryType() {
        return ShowBalanceQuery.class;
    }

    @Override
    public int getHandlerPriority() {
        return 20; // Высокий приоритет для информации о балансе
    }

    @Override
    public boolean supportsCaching() {
        return true; // Поддерживаем кэширование для баланса
    }

    @Override
    public String getDescription() {
        return "Обработчик запросов просмотра баланса с поддержкой истории и статистики";
    }

    /**
     * Определение типа контента на основе запроса
     */
    private String determineContentType(ShowBalanceQuery query) {
        if (query.isIncludeHistory() && query.isIncludeStatistics()) {
            return "BALANCE_DETAILS";
        } else if (query.isIncludeHistory() || query.isIncludeStatistics()) {
            return "DUAL_BALANCE_INFO";
        } else {
            return "BALANCE_SUMMARY";
        }
    }

    /**
     * Извлечение данных баланса из результата
     * ИСПРАВЛЕНО: Использует BalanceResponseMapper для преобразования типов
     */
    private DualBalanceResponse extractBalanceData(Object balanceResult, Long userId) {
        try {
            // Проверяем, что результат является Result<BalanceResponse>
            if (balanceResult instanceof shit.back.application.balance.common.Result) {
                var result = (shit.back.application.balance.common.Result<?>) balanceResult;

                if (result.isSuccess()
                        && result.getValue() instanceof shit.back.application.balance.dto.response.BalanceResponse) {
                    // Преобразуем BalanceResponse в DualBalanceResponse через маппер
                    var balanceResponse = (shit.back.application.balance.dto.response.BalanceResponse) result
                            .getValue();
                    return BalanceResponseMapper.toDualBalanceResponse(balanceResponse);
                }
            }

            // Попытка получить DualBalanceResponse напрямую (для обратной совместимости)
            if (balanceResult instanceof DualBalanceResponse) {
                return (DualBalanceResponse) balanceResult;
            }

            log.warn("⚠️ Неожиданный тип результата баланса: {}, создаем пустой баланс для пользователя {}",
                    balanceResult != null ? balanceResult.getClass().getSimpleName() : "null", userId);

            // Создаем пустой баланс для пользователя
            return BalanceResponseMapper.createEmptyDualBalance(userId);

        } catch (Exception e) {
            log.error("❌ Ошибка извлечения данных баланса: {}", e.getMessage(), e);
            // В случае ошибки возвращаем пустой баланс
            return BalanceResponseMapper.createEmptyDualBalance(userId);
        }
    }

}