package shit.back.service.payment;

import shit.back.entity.PaymentEntity;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Strategy интерфейс для обработки различных методов платежей
 * Заменяет switch-case логику в PaymentService на паттерн Strategy
 */
public interface PaymentStrategy {

    /**
     * Получить название метода платежа
     */
    String getPaymentMethodName();

    /**
     * Проверить, поддерживается ли данный метод платежа
     */
    boolean isSupported();

    /**
     * Создать ссылку для оплаты
     * 
     * @param payment платежная сущность
     * @return URL для оплаты или null если не удалось создать
     */
    String createPaymentLink(PaymentEntity payment);

    /**
     * Верифицировать callback от платежной системы
     * 
     * @param payment платежная сущность
     * @param params  параметры callback
     * @return true если callback валидный
     */
    boolean verifyCallback(PaymentEntity payment, Map<String, String> params);

    /**
     * Получить информацию о конфигурации метода платежа
     */
    PaymentMethodInfo getMethodInfo();

    /**
     * Обработать платеж
     * 
     * @param userId      ID пользователя
     * @param amount      сумма платежа
     * @param description описание платежа
     * @return результат обработки платежа
     */
    PaymentResult processPayment(Long userId, BigDecimal amount, String description);

    /**
     * Информация о методе платежа
     */
    record PaymentMethodInfo(
            String name,
            String displayName,
            boolean enabled,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String currency,
            Map<String, Object> metadata) {
    }

    /**
     * Результат обработки платежа
     */
    record PaymentResult(
            boolean success,
            String paymentId,
            String paymentUrl,
            String errorMessage,
            Map<String, Object> metadata) {
        public static PaymentResult success(String paymentId, String paymentUrl) {
            return new PaymentResult(true, paymentId, paymentUrl, null, Map.of());
        }

        public static PaymentResult success(String paymentId, String paymentUrl, Map<String, Object> metadata) {
            return new PaymentResult(true, paymentId, paymentUrl, null, metadata);
        }

        public static PaymentResult failure(String errorMessage) {
            return new PaymentResult(false, null, null, errorMessage, Map.of());
        }

        public static PaymentResult failure(String errorMessage, Map<String, Object> metadata) {
            return new PaymentResult(false, null, null, errorMessage, metadata);
        }
    }
}