package shit.back.telegram.queries.handlers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import shit.back.application.balance.dto.response.SimpleBalanceResponse;
import shit.back.application.balance.service.BalanceApplicationFacade;
import shit.back.application.balance.mapper.BalanceResponseMapper;
import shit.back.telegram.dto.TelegramResponse;
import shit.back.telegram.queries.ShowBalanceQuery;
import shit.back.telegram.queries.TelegramQueryHandler;
import shit.back.telegram.ui.strategy.BalanceDisplayStrategy;
import shit.back.telegram.ui.strategy.SimplifiedBalanceDisplayStrategy;
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

    @Autowired
    private SimplifiedBalanceDisplayStrategy simplifiedBalanceDisplayStrategy;

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
                String errorCode = balanceResult.getError() != null
                        ? balanceResult.getError().getClass().getSimpleName()
                        : "UNKNOWN_ERROR";
                String errorMessage = balanceResult.getError() != null ? balanceResult.getError().getMessage()
                        : "Неизвестная ошибка";

                log.error("Balance retrieval failed for user {}", query.getUserId());

                // Возвращаем детализированную ошибку
                String userFriendlyMessage = determineUserFriendlyErrorMessage(errorCode, errorMessage);
                return TelegramResponse.error(userFriendlyMessage);
            }

            // ФАЗА 2: Проверяем, можно ли использовать упрощенную архитектуру
            boolean useSimplified = shouldUseSimplifiedDisplay(query);

            if (useSimplified) {
                log.info("🌟 ФАЗА2: Использование упрощенной архитектуры для пользователя {}", query.getUserId());
                return handleSimplifiedBalance(balanceResult, query);
            } else {
                log.info("🔄 ФАЗА2: Использование старой DualBalance архитектуры для пользователя {}",
                        query.getUserId());
                return handleLegacyBalance(balanceResult, query);
            }

        } catch (Exception e) {
            log.error("Balance retrieval failed for user {}", query.getUserId());
            return TelegramResponse.error("Не удалось получить информацию о балансе");
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
    private SimpleBalanceResponse extractBalanceData(Object balanceResult, Long userId) {
        try {
            // ДИАГНОСТИЧЕСКИЙ ЛОГ: Проверяем тип результата
            log.debug("🔍 ДИАГНОСТИКА: Тип результата баланса: {}",
                    balanceResult != null ? balanceResult.getClass().getSimpleName() : "null");

            // Проверяем, что результат является Result<BalanceResponse>
            if (balanceResult instanceof shit.back.application.balance.common.Result) {
                var result = (shit.back.application.balance.common.Result<?>) balanceResult;

                if (result.isSuccess()
                        && result.getValue() instanceof shit.back.application.balance.dto.response.BalanceResponse) {
                    // Преобразуем BalanceResponse в SimpleBalanceResponse через маппер
                    var balanceResponse = (shit.back.application.balance.dto.response.BalanceResponse) result
                            .getValue();

                    // ДИАГНОСТИЧЕСКИЙ ЛОГ: Исходные данные BalanceResponse
                    log.debug("🔍 ДИАГНОСТИКА: userId={}, BalanceResponse.currentBalance={}, currency={}",
                            userId,
                            balanceResponse.getCurrentBalance(),
                            balanceResponse.getCurrency());

                    SimpleBalanceResponse dualResponse = BalanceResponseMapper.toSimpleBalanceResponse(balanceResponse);

                    // ДИАГНОСТИЧЕСКИЙ ЛОГ: Результат маппинга
                    log.debug("🔍 ДИАГНОСТИКА: userId={}, SimpleBalance после маппинга - currentBalance={}",
                            userId,
                            dualResponse.getFormattedBalance());

                    return dualResponse;
                }
            }

            // Попытка получить SimpleBalanceResponse напрямую (для обратной совместимости)
            if (balanceResult instanceof SimpleBalanceResponse) {
                return (SimpleBalanceResponse) balanceResult;
            }

            log.warn("⚠️ Неожиданный тип результата баланса: {}, создаем пустой баланс для пользователя {}",
                    balanceResult != null ? balanceResult.getClass().getSimpleName() : "null", userId);

            // Создаем пустой баланс для пользователя
            return BalanceResponseMapper.createEmptyBalance(userId);

        } catch (Exception e) {
            log.error("Balance data extraction failed for user {}", userId);
            // В случае ошибки возвращаем пустой баланс
            return BalanceResponseMapper.createEmptyBalance(userId);
        }
    }

    /**
     * ФАЗА 2: Определяет, следует ли использовать упрощенную архитектуру
     */
    private boolean shouldUseSimplifiedDisplay(ShowBalanceQuery query) {
        // Простая логика: используем упрощенную архитектуру для базовых запросов
        return !query.isIncludeHistory() && !query.isIncludeStatistics();
    }

    /**
     * ФАЗА 2: Обработка баланса с упрощенной архитектурой
     */
    private TelegramResponse handleSimplifiedBalance(Object balanceResult, ShowBalanceQuery query) {
        try {
            // Конвертируем в SimpleBalanceResponse
            SimpleBalanceResponse simpleBalance = convertToSimpleBalance(balanceResult, query.getUserId());
            if (simpleBalance == null) {
                return TelegramResponse.error("Не удалось получить упрощенную информацию о балансе");
            }

            // Используем упрощенную стратегию
            var uiResponse = simplifiedBalanceDisplayStrategy.createBalanceDisplay(simpleBalance);

            // Конвертируем TelegramUIResponse в TelegramResponse
            return TelegramResponse.builder()
                    .successful(true)
                    .message(uiResponse.getMessageText())
                    .uiType("SIMPLE_BALANCE")
                    .uiData(simpleBalance)
                    .data(uiResponse.getKeyboard())
                    .build();

        } catch (Exception e) {
            log.error("Simplified balance processing failed for user {}", query.getUserId());
            return TelegramResponse.error("Ошибка при получении упрощенного баланса");
        }
    }

    /**
     * ФАЗА 2: Обработка баланса со старой DualBalance архитектурой
     */
    private TelegramResponse handleLegacyBalance(Object balanceResult, ShowBalanceQuery query) {
        try {
            // Получаем SimpleBalanceResponse из результата
            SimpleBalanceResponse balanceData = extractBalanceData(balanceResult, query.getUserId());
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
                    .newRow()
                    .addButton("📋 История", "show_history")
                    .addButton("💳 Пополнить", "topup_balance")
                    .newRow()
                    .addButton("🔄 Обновить", "refresh_balance")
                    .build();

            log.info("✅ LEGACY: Баланс успешно получен для пользователя: {}", query.getUserId());

            return TelegramResponse.builder()
                    .successful(true)
                    .message(formattedMessage)
                    .uiType(contentType)
                    .uiData(balanceData)
                    .data(keyboard)
                    .build();

        } catch (Exception e) {
            log.error("Legacy balance processing failed for user {}", query.getUserId());
            return TelegramResponse.error("Ошибка при получении баланса");
        }
    }

    /**
     * Определение пользовательского сообщения об ошибке на основе технических
     * деталей
     */
    private String determineUserFriendlyErrorMessage(String errorCode, String errorMessage) {
        switch (errorCode) {
            case "InvalidTransactionException":
                if (errorMessage.contains("BALANCE_NOT_FOUND")) {
                    return "🚫 Баланс не найден. Создаем новый баланс...";
                } else if (errorMessage.contains("BALANCE_RETRIEVAL_ERROR")) {
                    return "⚠️ Временная ошибка получения баланса. Попробуйте позже";
                }
                break;
            case "BalanceDomainException":
                return "🔧 Ошибка системы баланса. Обратитесь в поддержку";
            case "InsufficientFundsException":
                return "💸 Недостаточно средств на балансе";
            default:
                break;
        }

        // Общее сообщение для неизвестных ошибок
        return "❌ Не удалось получить информацию о балансе. Попробуйте позже";
    }

    /**
     * ФАЗА 2: Конвертация в SimpleBalanceResponse из результата баланса
     */
    private SimpleBalanceResponse convertToSimpleBalance(Object balanceResult, Long userId) {
        try {
            if (balanceResult instanceof shit.back.application.balance.common.Result) {
                var result = (shit.back.application.balance.common.Result<?>) balanceResult;

                if (result.isSuccess()
                        && result.getValue() instanceof shit.back.application.balance.dto.response.BalanceResponse) {
                    var balanceResponse = (shit.back.application.balance.dto.response.BalanceResponse) result
                            .getValue();

                    log.debug("🌟 ФАЗА2: Конвертация BalanceResponse в SimpleBalance для userId={}", userId);
                    return BalanceResponseMapper.toSimpleBalanceResponse(balanceResponse);
                }
            }

            log.warn("⚠️ ФАЗА2: Не удалось конвертировать в SimpleBalance, создаем пустой для userId={}", userId);
            return BalanceResponseMapper.createEmptyBalance(userId);

        } catch (Exception e) {
            log.error("SimpleBalance conversion failed for user {}", userId);
            return BalanceResponseMapper.createEmptyBalance(userId);
        }
    }

}