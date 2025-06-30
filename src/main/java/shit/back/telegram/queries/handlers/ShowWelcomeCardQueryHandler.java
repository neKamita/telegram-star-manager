package shit.back.telegram.queries.handlers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import shit.back.application.balance.dto.response.DualBalanceResponse;
import shit.back.application.balance.service.BalanceApplicationFacade;
import shit.back.telegram.dto.TelegramResponse;
import shit.back.telegram.queries.ShowWelcomeCardQuery;
import shit.back.telegram.queries.TelegramQueryHandler;
import shit.back.telegram.ui.strategy.WelcomeCardStrategy;
import shit.back.telegram.ui.builder.TelegramKeyboardBuilder;

/**
 * Обработчик запроса приветственной карточки
 * 
 * Интегрируется с WelcomeCardStrategy для отображения приветствия
 */
@Component
@Slf4j
public class ShowWelcomeCardQueryHandler implements TelegramQueryHandler<ShowWelcomeCardQuery> {

    @Autowired
    private WelcomeCardStrategy welcomeCardStrategy;

    @Autowired
    private BalanceApplicationFacade balanceApplicationFacade;

    @Override
    @Transactional(readOnly = true)
    public TelegramResponse handle(ShowWelcomeCardQuery query) throws Exception {
        log.info("👋 Запрос приветственной карточки: userId={}, type={}, user={}, includeBalance={}",
                query.getUserId(), query.getCardType(), query.getUserName(), query.isIncludeBalance());

        try {
            // Валидация запроса
            query.validate();

            // Подготовка данных для стратегии
            Object strategyData = prepareStrategyData(query);

            // Форматируем сообщение через стратегию
            String formattedMessage = welcomeCardStrategy.formatContent(query.getCardType(), strategyData);

            // Создаем клавиатуру в зависимости от типа карточки
            var keyboardBuilder = new TelegramKeyboardBuilder();

            switch (query.getCardType()) {
                case "USER_WELCOME_CARD" -> {
                    keyboardBuilder
                            .addButton("💰 Баланс", "show_balance")
                            .addButton("⭐ Купить звезды", "buy_stars")
                            .newRow()
                            .addButton("💳 Пополнить", "topup_balance")
                            .addButton("📋 История", "show_history")
                            .newRow()
                            .addButton("❓ Помощь", "show_help");
                }
                case "PAYMENT_METHODS_CARD" -> {
                    keyboardBuilder
                            .addButton("💳 Пополнить баланс", "topup_balance")
                            .newRow()
                            .addButton("🔙 Назад", "show_welcome");
                }
                case "ONBOARDING_CARD" -> {
                    keyboardBuilder
                            .addButton("🚀 Начать", "show_welcome")
                            .addButton("❓ Помощь", "show_help");
                }
            }

            var keyboard = keyboardBuilder.build();

            log.info("✅ Приветственная карточка успешно создана для пользователя: {}", query.getUserId());

            return TelegramResponse.builder()
                    .successful(true)
                    .message(formattedMessage)
                    .uiType(query.getCardType())
                    .uiData(strategyData)
                    .data(keyboard)
                    .build();

        } catch (IllegalArgumentException e) {
            log.warn("❌ Некорректный запрос приветственной карточки от пользователя {}: {}",
                    query.getUserId(), e.getMessage());
            return TelegramResponse.error("❌ " + e.getMessage());

        } catch (Exception e) {
            log.error("❌ Ошибка при создании приветственной карточки для пользователя {}: {}",
                    query.getUserId(), e.getMessage(), e);
            return TelegramResponse.error("Не удалось создать приветственную карточку: " + e.getMessage());
        }
    }

    @Override
    public Class<ShowWelcomeCardQuery> getQueryType() {
        return ShowWelcomeCardQuery.class;
    }

    @Override
    public int getHandlerPriority() {
        return 15; // Высокий приоритет для приветствия
    }

    @Override
    public boolean supportsCaching() {
        return true; // Кэшируем приветственные карточки
    }

    @Override
    public String getDescription() {
        return "Обработчик запросов приветственных карточек с интеграцией WelcomeCardStrategy";
    }

    /**
     * Подготовка данных для стратегии в зависимости от типа карточки
     */
    private Object prepareStrategyData(ShowWelcomeCardQuery query) throws Exception {
        return switch (query.getCardType()) {
            case "USER_WELCOME_CARD" -> prepareUserWelcomeData(query);
            case "PAYMENT_METHODS_CARD" -> query.getCurrency();
            case "ONBOARDING_CARD" -> query.getUserName();
            default -> throw new IllegalArgumentException("Неподдерживаемый тип карточки: " + query.getCardType());
        };
    }

    /**
     * Подготовка данных для пользовательской приветственной карточки
     */
    private WelcomeCardStrategy.WelcomeCardData prepareUserWelcomeData(ShowWelcomeCardQuery query) {
        DualBalanceResponse balanceData = null;

        if (query.isIncludeBalance()) {
            try {
                // Получаем баланс пользователя
                var balanceResult = balanceApplicationFacade.getBalance(query.getUserId());

                if (balanceResult != null && balanceResult.isSuccess()) {
                    balanceData = extractBalanceData(balanceResult);
                }

                if (balanceData != null) {
                    log.debug("✅ Баланс получен для приветственной карточки: userId={}", query.getUserId());
                } else {
                    log.warn("⚠️ Не удалось получить баланс для приветственной карточки: userId={}", query.getUserId());
                }

            } catch (Exception e) {
                log.warn("⚠️ Ошибка получения баланса для приветственной карточки: userId={}, error={}",
                        query.getUserId(), e.getMessage());
                // Продолжаем без баланса
                balanceData = null;
            }
        }

        return new WelcomeCardStrategy.WelcomeCardData(query.getUserName(), balanceData);
    }

    /**
     * Извлечение данных баланса из результата
     */
    private DualBalanceResponse extractBalanceData(Object balanceResult) {
        try {
            if (balanceResult instanceof DualBalanceResponse) {
                return (DualBalanceResponse) balanceResult;
            }

            // TODO: Добавить маппинг других типов если необходимо
            log.debug("🔍 Неожиданный тип результата баланса для приветственной карточки: {}",
                    balanceResult != null ? balanceResult.getClass().getSimpleName() : "null");
            return null;

        } catch (Exception e) {
            log.warn("⚠️ Ошибка извлечения данных баланса для приветственной карточки: {}", e.getMessage());
            return null;
        }
    }
}