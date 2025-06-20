package shit.back.service.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import shit.back.entity.BalanceTransactionEntity;
import shit.back.entity.PaymentEntity;
import shit.back.service.BalanceService;
import shit.back.config.SystemConfigurationProperties;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Стратегия для обработки платежей через баланс пользователя
 * Основной и предпочтительный метод платежа в системе
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BalancePaymentStrategy implements PaymentStrategy {

    private final BalanceService balanceService;
    private final SystemConfigurationProperties systemConfig;

    @Override
    public String getPaymentMethodName() {
        return "BALANCE";
    }

    @Override
    public boolean isSupported() {
        return true; // Всегда поддерживается
    }

    @Override
    public String createPaymentLink(PaymentEntity payment) {
        // Для балансовых платежей не нужны внешние ссылки
        // Возвращаем внутреннюю ссылку для обработки
        String baseUrl = systemConfig.getUrls().getWebhookBaseUrl();
        return String.format("%s/payment/balance/process?paymentId=%s",
                baseUrl, payment.getPaymentId());
    }

    @Override
    public boolean verifyCallback(PaymentEntity payment, Map<String, String> params) {
        // Для балансовых платежей callback не требуется
        // Всегда возвращаем true для внутренних операций
        log.debug("Balance payment verification for payment: {}", payment.getPaymentId());
        return true;
    }

    @Override
    public PaymentMethodInfo getMethodInfo() {
        return new PaymentMethodInfo(
                "BALANCE",
                "Баланс аккаунта",
                true,
                new BigDecimal("0.01"), // Минимальная сумма
                new BigDecimal("100000.00"), // Максимальная сумма
                "USD",
                Map.of(
                        "type", "internal",
                        "instant", true,
                        "fees", new BigDecimal("0.00"),
                        "description", "Оплата с баланса аккаунта пользователя"));
    }

    @Override
    public PaymentResult processPayment(Long userId, BigDecimal amount, String description) {
        log.info("Processing balance payment for user {}: {} USD", userId, amount);

        try {
            // Проверяем достаточность средств
            boolean hasSufficientBalance = balanceService.checkSufficientBalance(userId, amount);
            if (!hasSufficientBalance) {
                log.warn("Insufficient balance for user {}: required {}", userId, amount);
                return PaymentResult.failure(
                        "Недостаточно средств на балансе",
                        Map.of(
                                "requiredAmount", amount,
                                "errorCode", "INSUFFICIENT_BALANCE"));
            }

            // Генерируем уникальный ID для транзакции
            String transactionId = generateTransactionId();

            // Выполняем списание с баланса
            BalanceTransactionEntity transaction = balanceService.withdraw(userId, amount, description);

            log.info("Balance payment successful for user {}: transaction {}",
                    userId, transaction.getTransactionId());

            return PaymentResult.success(
                    transaction.getTransactionId(),
                    null, // URL не нужен для внутренних операций
                    Map.of(
                            "transactionId", transaction.getTransactionId(),
                            "balanceAfter", transaction.getBalanceAfter(),
                            "processingTime", System.currentTimeMillis(),
                            "method", "BALANCE"));

        } catch (Exception e) {
            log.error("Error processing balance payment for user {}: {}", userId, e.getMessage(), e);
            return PaymentResult.failure(
                    "Ошибка при обработке платежа: " + e.getMessage(),
                    Map.of(
                            "errorCode", "PROCESSING_ERROR",
                            "errorDetails", e.getMessage()));
        }
    }

    /**
     * Проверить доступность метода для пользователя
     */
    public boolean isAvailableForUser(Long userId, BigDecimal amount) {
        try {
            return balanceService.checkSufficientBalance(userId, amount);
        } catch (Exception e) {
            log.error("Error checking balance availability for user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * Получить текущий баланс пользователя
     */
    public BigDecimal getUserBalance(Long userId) {
        try {
            var balanceStats = balanceService.getBalanceStatistics(userId);
            return (BigDecimal) balanceStats.get("currentBalance");
        } catch (Exception e) {
            log.error("Error getting user balance for user {}: {}", userId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * Резервировать средства для будущего платежа
     */
    public boolean reserveFunds(Long userId, BigDecimal amount, String orderId) {
        try {
            balanceService.reserveBalance(userId, amount, orderId);
            return true;
        } catch (Exception e) {
            log.error("Error reserving funds for user {} and order {}: {}",
                    userId, orderId, e.getMessage());
            return false;
        }
    }

    /**
     * Освободить зарезервированные средства
     */
    public void releaseFunds(Long userId, String orderId) {
        try {
            balanceService.releaseReservedBalance(userId, orderId);
        } catch (Exception e) {
            log.error("Error releasing reserved funds for user {} and order {}: {}",
                    userId, orderId, e.getMessage());
        }
    }

    private String generateTransactionId() {
        return "BAL_" + System.currentTimeMillis() + "_" +
                (int) (Math.random() * 9999);
    }
}