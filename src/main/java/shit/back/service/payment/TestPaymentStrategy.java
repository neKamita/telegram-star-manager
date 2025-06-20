package shit.back.service.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import shit.back.entity.PaymentEntity;
import shit.back.config.SystemConfigurationProperties;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Тестовая стратегия платежей для разработки и тестирования
 * Симулирует внешние платежные системы без реальных транзакций
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment.test.enabled", havingValue = "true")
public class TestPaymentStrategy implements PaymentStrategy {

    private final SystemConfigurationProperties systemConfig;

    @Override
    public String getPaymentMethodName() {
        return "TEST";
    }

    @Override
    public boolean isSupported() {
        return true; // Всегда поддерживается в тестовом режиме
    }

    @Override
    public String createPaymentLink(PaymentEntity payment) {
        log.debug("Creating test payment link for payment: {}", payment.getPaymentId());

        // Создаем тестовую ссылку с параметрами
        String baseUrl = systemConfig.getUrls().getWebhookBaseUrl();
        String testUrl = String.format(
                "%s/payment/test/process?paymentId=%s&amount=%s&testMode=true",
                baseUrl,
                payment.getPaymentId(),
                payment.getAmount());

        log.info("Test payment link created: {}", testUrl);
        return testUrl;
    }

    @Override
    public boolean verifyCallback(PaymentEntity payment, Map<String, String> params) {
        log.debug("Verifying test callback for payment: {}", payment.getPaymentId());

        // Для тестовых платежей всегда возвращаем успех
        // Можно добавить логику для симуляции ошибок
        String testResult = params.get("test_result");
        if ("failure".equals(testResult)) {
            log.info("Test payment callback indicates failure for payment: {}", payment.getPaymentId());
            return false;
        }

        log.info("Test payment callback verified successfully for payment: {}", payment.getPaymentId());
        return true;
    }

    @Override
    public PaymentMethodInfo getMethodInfo() {
        return new PaymentMethodInfo(
                "TEST",
                "Тестовые платежи",
                true,
                new BigDecimal("0.01"),
                new BigDecimal("10000.00"),
                "USD",
                Map.of(
                        "type", "test",
                        "instant", true,
                        "fees", new BigDecimal("0.00"),
                        "description", "Тестовый метод платежа для разработки",
                        "simulatesDelay", true,
                        "canSimulateFailures", true));
    }

    @Override
    public PaymentResult processPayment(Long userId, BigDecimal amount, String description) {
        log.info("Processing test payment for user {}: {} USD", userId, amount);

        try {
            // Симулируем задержку обработки
            Thread.sleep(getSimulatedProcessingDelay());

            // Симулируем различные результаты
            PaymentSimulationResult simulation = simulatePaymentResult(amount);

            if (simulation.success()) {
                String testPaymentId = generateTestPaymentId();
                String testPaymentUrl = createTestSuccessUrl(testPaymentId);

                log.info("Test payment successful for user {}: paymentId={}", userId, testPaymentId);

                return PaymentResult.success(
                        testPaymentId,
                        testPaymentUrl,
                        Map.of(
                                "testMode", true,
                                "simulatedProcessingTime", simulation.processingTime(),
                                "testPaymentId", testPaymentId,
                                "simulatedFees", new BigDecimal("0.00"),
                                "testDescription", description));
            } else {
                log.warn("Test payment failed for user {}: {}", userId, simulation.errorMessage());

                return PaymentResult.failure(
                        simulation.errorMessage(),
                        Map.of(
                                "testMode", true,
                                "errorCode", simulation.errorCode(),
                                "simulatedProcessingTime", simulation.processingTime()));
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Test payment processing interrupted for user {}", userId);
            return PaymentResult.failure(
                    "Обработка платежа прервана",
                    Map.of("testMode", true, "errorCode", "INTERRUPTED"));
        } catch (Exception e) {
            log.error("Error processing test payment for user {}: {}", userId, e.getMessage(), e);
            return PaymentResult.failure(
                    "Ошибка тестового платежа: " + e.getMessage(),
                    Map.of("testMode", true, "errorCode", "PROCESSING_ERROR"));
        }
    }

    /**
     * Симулирует результат обработки платежа
     */
    private PaymentSimulationResult simulatePaymentResult(BigDecimal amount) {
        long processingTime = ThreadLocalRandom.current().nextLong(500, 3000);

        // Симулируем успех в 90% случаев
        boolean success = ThreadLocalRandom.current().nextDouble() < 0.9;

        if (success) {
            return new PaymentSimulationResult(true, null, null, processingTime);
        } else {
            // Симулируем различные типы ошибок
            String[] errorCodes = { "INSUFFICIENT_FUNDS", "NETWORK_ERROR", "TIMEOUT", "DECLINED" };
            String[] errorMessages = {
                    "Недостаточно средств на карте",
                    "Ошибка сети при обработке платежа",
                    "Превышено время ожидания",
                    "Платеж отклонен банком"
            };

            int errorIndex = ThreadLocalRandom.current().nextInt(errorCodes.length);
            return new PaymentSimulationResult(
                    false,
                    errorCodes[errorIndex],
                    errorMessages[errorIndex],
                    processingTime);
        }
    }

    private long getSimulatedProcessingDelay() {
        // Возвращаем случайную задержку от 100 до 2000 мс
        return ThreadLocalRandom.current().nextLong(100, 2000);
    }

    private String generateTestPaymentId() {
        return "TEST_" + System.currentTimeMillis() + "_" +
                ThreadLocalRandom.current().nextInt(1000, 9999);
    }

    private String createTestSuccessUrl(String paymentId) {
        String baseUrl = systemConfig.getUrls().getWebhookBaseUrl();
        return String.format("%s/payment/test/success?paymentId=%s", baseUrl, paymentId);
    }

    /**
     * Результат симуляции платежа
     */
    private record PaymentSimulationResult(
            boolean success,
            String errorCode,
            String errorMessage,
            long processingTime) {
    }
}