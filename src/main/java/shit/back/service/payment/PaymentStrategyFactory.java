package shit.back.service.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory для управления стратегиями платежей
 * Заменяет switch-case логику в PaymentService
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentStrategyFactory {

    private final List<PaymentStrategy> paymentStrategies;

    private Map<String, PaymentStrategy> strategyMap;

    /**
     * Инициализация factory после создания всех стратегий
     */
    public void initializeStrategies() {
        if (strategyMap == null) {
            strategyMap = paymentStrategies.stream()
                    .filter(PaymentStrategy::isSupported)
                    .collect(Collectors.toMap(
                            strategy -> strategy.getPaymentMethodName().toLowerCase(),
                            Function.identity()));

            log.info("Initialized payment strategies: {}", strategyMap.keySet());
        }
    }

    /**
     * Получить стратегию для указанного метода платежа
     */
    public Optional<PaymentStrategy> getStrategy(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.trim().isEmpty()) {
            log.warn("Payment method is null or empty");
            return Optional.empty();
        }

        initializeStrategies();

        String normalizedMethod = paymentMethod.toLowerCase().trim();
        PaymentStrategy strategy = strategyMap.get(normalizedMethod);

        if (strategy == null) {
            log.warn("No strategy found for payment method: {}", paymentMethod);
            return Optional.empty();
        }

        log.debug("Found strategy for payment method {}: {}",
                paymentMethod, strategy.getClass().getSimpleName());
        return Optional.of(strategy);
    }

    /**
     * Проверить, поддерживается ли метод платежа
     */
    public boolean isPaymentMethodSupported(String paymentMethod) {
        return getStrategy(paymentMethod).isPresent();
    }

    /**
     * Получить все поддерживаемые методы платежей
     */
    public List<String> getSupportedPaymentMethods() {
        initializeStrategies();
        return List.copyOf(strategyMap.keySet());
    }

    /**
     * Получить информацию о всех поддерживаемых методах платежей
     */
    public List<PaymentStrategy.PaymentMethodInfo> getAllPaymentMethodsInfo() {
        initializeStrategies();
        return strategyMap.values().stream()
                .map(PaymentStrategy::getMethodInfo)
                .collect(Collectors.toList());
    }

    /**
     * Получить информацию о конкретном методе платежа
     */
    public Optional<PaymentStrategy.PaymentMethodInfo> getPaymentMethodInfo(String paymentMethod) {
        return getStrategy(paymentMethod)
                .map(PaymentStrategy::getMethodInfo);
    }

    /**
     * Получить предпочтительную стратегию платежа
     * В нашем случае это всегда BALANCE
     */
    public PaymentStrategy getPreferredStrategy() {
        return getStrategy("BALANCE")
                .orElseThrow(() -> new IllegalStateException("Balance payment strategy not available"));
    }

    /**
     * Получить fallback стратегию (для тестирования)
     */
    public Optional<PaymentStrategy> getFallbackStrategy() {
        return getStrategy("TEST");
    }

    /**
     * Проверить доступность стратегий для пользователя
     */
    public List<PaymentStrategy> getAvailableStrategiesForUser(Long userId) {
        initializeStrategies();

        return strategyMap.values().stream()
                .filter(strategy -> {
                    try {
                        // Для балансовой стратегии проверяем дополнительно
                        if (strategy instanceof BalancePaymentStrategy balanceStrategy) {
                            // Проверяем, есть ли у пользователя баланс
                            return balanceStrategy.getUserBalance(userId)
                                    .compareTo(java.math.BigDecimal.ZERO) > 0;
                        }

                        // Для остальных стратегий проверяем базовую поддержку
                        return strategy.isSupported();
                    } catch (Exception e) {
                        log.warn("Error checking strategy availability for user {}: {}",
                                userId, e.getMessage());
                        return false;
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * Получить статистику использования стратегий
     */
    public Map<String, Object> getStrategyStatistics() {
        initializeStrategies();

        return Map.of(
                "totalStrategies", strategyMap.size(),
                "supportedMethods", getSupportedPaymentMethods(),
                "preferredMethod", "BALANCE",
                "fallbackAvailable", getFallbackStrategy().isPresent(),
                "strategiesInfo", getAllPaymentMethodsInfo());
    }
}