package shit.back.telegram.ui.factory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import shit.back.telegram.ui.strategy.TelegramMessageStrategy;
import shit.back.telegram.ui.strategy.BalanceDisplayStrategy;
import shit.back.telegram.ui.strategy.StarPurchaseFlowStrategy;
import shit.back.telegram.ui.strategy.WelcomeCardStrategy;
import shit.back.telegram.ui.strategy.PurchaseHistoryStrategy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory для создания форматированных Telegram сообщений
 *
 * Использует Strategy паттерн для различных типов сообщений
 * с автоматической регистрацией всех доступных стратегий
 */
@Component
public class TelegramMessageFactory {

    private final Map<String, TelegramMessageStrategy> strategies = new HashMap<>();

    @Autowired
    private BalanceDisplayStrategy balanceDisplayStrategy;

    @Autowired
    private StarPurchaseFlowStrategy starPurchaseFlowStrategy;

    @Autowired
    private WelcomeCardStrategy welcomeCardStrategy;

    @Autowired
    private PurchaseHistoryStrategy purchaseHistoryStrategy;

    /**
     * Инициализация с автоматической регистрацией стратегий
     */
    @Autowired
    public void initializeStrategies() {
        // Регистрируем все доступные стратегии
        registerStrategy(balanceDisplayStrategy);
        registerStrategy(starPurchaseFlowStrategy);
        registerStrategy(welcomeCardStrategy);
        registerStrategy(purchaseHistoryStrategy);
    }

    /**
     * Регистрация стратегии форматирования
     */
    public void registerStrategy(TelegramMessageStrategy strategy) {
        for (String contentType : strategy.getSupportedContentTypes()) {
            strategies.put(contentType, strategy);
        }
    }

    /**
     * Форматирование контента по типу
     */
    public String formatContent(String contentType, Object data) {
        TelegramMessageStrategy strategy = strategies.get(contentType);
        if (strategy == null) {
            throw new IllegalArgumentException("Неподдерживаемый тип контента: " + contentType);
        }
        return strategy.formatContent(contentType, data);
    }

    /**
     * Проверка поддержки типа контента
     */
    public boolean supports(String contentType) {
        return strategies.containsKey(contentType);
    }

    /**
     * Получить список доступных стратегий
     */
    public List<String> getAvailableStrategies() {
        return strategies.values().stream()
                .map(TelegramMessageStrategy::getStrategyType)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Получить список поддерживаемых типов контента
     */
    public List<String> getSupportedContentTypes() {
        return strategies.keySet().stream()
                .sorted()
                .toList();
    }
}