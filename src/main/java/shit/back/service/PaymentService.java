package shit.back.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shit.back.config.PaymentConfigurationProperties;
import shit.back.config.SystemConfigurationProperties;
import shit.back.entity.PaymentEntity;
import shit.back.entity.PaymentStatus;
import shit.back.entity.UserBalanceEntity;
import shit.back.repository.PaymentJpaRepository;
import shit.back.service.payment.PaymentStrategyFactory;
import shit.back.service.payment.PaymentStrategy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Рефакторенный сервис для обработки платежей ТОЛЬКО через баланс
 * Использует Strategy Pattern для расширяемости
 */
@Slf4j
@Service
public class PaymentService {

    @Autowired
    private PaymentJpaRepository paymentRepository;

    @Autowired
    private PaymentConfigurationProperties paymentConfig;

    @Autowired
    private SystemConfigurationProperties systemConfig;

    @Autowired
    private PaymentStrategyFactory paymentStrategyFactory;

    @Autowired
    private BalanceService balanceService;

    /**
     * Создать новый платеж
     */
    @Transactional
    public PaymentEntity createPayment(Long userId, BigDecimal amount, String paymentMethod, String orderId,
            String description) {
        log.info("💳 Создание платежа для пользователя {}: сумма={}, метод={}, заказ={}",
                userId, amount, paymentMethod, orderId);

        validatePaymentData(userId, amount, paymentMethod);

        String paymentId = generatePaymentId();
        String currency = "USD"; // По умолчанию USD, можно расширить

        PaymentEntity payment = new PaymentEntity(paymentId, userId, orderId, amount, currency, paymentMethod,
                description);
        payment.setExpiresAt(paymentConfig.getGeneral().getPaymentTimeoutMinutes());

        payment = paymentRepository.save(payment);
        log.info("✅ Платеж создан: ID={}, внутренний_ID={}", payment.getPaymentId(), payment.getId());

        return payment;
    }

    /**
     * Обработать платеж ТОЛЬКО через баланс (использует Strategy Pattern)
     */
    @Transactional
    public PaymentStrategy.PaymentResult processPayment(Long userId, BigDecimal amount, String description) {
        log.info("🔄 Обработка БАЛАНСОВОГО платежа для пользователя {}: сумма={}",
                userId, amount);

        try {
            validatePaymentData(userId, amount, "BALANCE");

            // Получаем предпочтительную стратегию (BALANCE)
            PaymentStrategy strategy = paymentStrategyFactory.getPreferredStrategy();

            // Обрабатываем платеж через стратегию
            PaymentStrategy.PaymentResult result = strategy.processPayment(userId, amount, description);

            if (result.success()) {
                log.info("✅ Балансовый платеж успешно обработан для пользователя {}: {}",
                        userId, result.paymentId());
            } else {
                log.warn("❌ Балансовый платеж не удался для пользователя {}: {}",
                        userId, result.errorMessage());
            }

            return result;

        } catch (Exception e) {
            log.error("❌ Ошибка при обработке балансового платежа для пользователя {}: {}",
                    userId, e.getMessage(), e);
            return PaymentStrategy.PaymentResult.failure(
                    "Ошибка при обработке платежа: " + e.getMessage(),
                    Map.of("errorCode", "PROCESSING_ERROR", "errorDetails", e.getMessage()));
        }
    }

    /**
     * Проверить доступность балансового платежа для пользователя
     */
    public boolean checkBalancePaymentAvailability(Long userId, BigDecimal amount) {
        log.debug("Проверка доступности балансового платежа для пользователя {}: сумма={}", userId, amount);

        try {
            PaymentStrategy strategy = paymentStrategyFactory.getPreferredStrategy();
            if (strategy instanceof shit.back.service.payment.BalancePaymentStrategy balanceStrategy) {
                return balanceStrategy.isAvailableForUser(userId, amount);
            }
            return false;
        } catch (Exception e) {
            log.error("Ошибка при проверке доступности балансового платежа: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Получить информацию о доступных методах платежа (ТОЛЬКО BALANCE)
     */
    public List<PaymentStrategy.PaymentMethodInfo> getAvailablePaymentMethods() {
        log.debug("Получение доступных методов платежа");
        return paymentStrategyFactory.getAllPaymentMethodsInfo();
    }

    /**
     * Получить статистику по стратегиям платежей
     */
    public Map<String, Object> getPaymentStrategiesStatistics() {
        return paymentStrategyFactory.getStrategyStatistics();
    }

    /**
     * Верифицировать callback ТОЛЬКО для балансовых платежей (используует Strategy
     * Pattern)
     */
    @Transactional
    public boolean verifyPaymentCallback(String paymentId, Map<String, String> params) {
        log.info("🔍 Верификация callback для БАЛАНСОВОГО платежа: {}", paymentId);

        try {
            Optional<PaymentEntity> paymentOpt = paymentRepository.findByPaymentId(paymentId);
            if (paymentOpt.isEmpty()) {
                log.warn("⚠️ Платеж не найден для callback: {}", paymentId);
                return false;
            }

            PaymentEntity payment = paymentOpt.get();

            // Используем Strategy Pattern для верификации
            PaymentStrategy strategy = paymentStrategyFactory.getPreferredStrategy();
            boolean isValid = strategy.verifyCallback(payment, params);

            if (isValid) {
                // Обновляем статус платежа и пополняем баланс
                processSuccessfulPayment(payment);
                log.info("✅ Callback успешно обработан для балансового платежа: {}", paymentId);
            } else {
                payment.updateStatus(PaymentStatus.FAILED, "Неверная подпись callback");
                paymentRepository.save(payment);
                log.warn("❌ Неверная подпись callback для платежа: {}", paymentId);
            }

            return isValid;

        } catch (Exception e) {
            log.error("❌ Ошибка при верификации callback для платежа {}: {}", paymentId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Обработать успешный платеж
     */
    @Transactional
    protected void processSuccessfulPayment(PaymentEntity payment) {
        log.info("💰 Обработка успешного платежа: ID={}, пользователь={}, сумма={}",
                payment.getPaymentId(), payment.getUserId(), payment.getAmount());

        try {
            // Обновляем статус платежа
            payment.updateStatus(PaymentStatus.COMPLETED);
            paymentRepository.save(payment);

            // Пополняем баланс пользователя
            if (payment.getUserId() != null) {
                balanceService.processBalancePayment(
                        payment.getUserId(),
                        payment.getPaymentId(),
                        payment.getAmount());

                log.info("✅ Баланс пользователя {} пополнен на сумму {}",
                        payment.getUserId(), payment.getAmount());
            }

        } catch (Exception e) {
            log.error("❌ Ошибка при обработке успешного платежа {}: {}",
                    payment.getPaymentId(), e.getMessage(), e);
            payment.updateStatus(PaymentStatus.FAILED, "Ошибка при зачислении средств");
            paymentRepository.save(payment);
            throw e;
        }
    }

    /**
     * Получить информацию о платеже
     */
    public Optional<PaymentEntity> getPayment(String paymentId) {
        return paymentRepository.findByPaymentId(paymentId);
    }

    /**
     * Получить платежи пользователя
     */
    public List<PaymentEntity> getUserPayments(Long userId) {
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Отменить платеж
     */
    @Transactional
    public boolean cancelPayment(String paymentId, String reason) {
        log.info("🚫 Отмена платежа: ID={}, причина={}", paymentId, reason);

        try {
            Optional<PaymentEntity> paymentOpt = paymentRepository.findByPaymentId(paymentId);
            if (paymentOpt.isEmpty()) {
                log.warn("⚠️ Платеж не найден для отмены: {}", paymentId);
                return false;
            }

            PaymentEntity payment = paymentOpt.get();
            if (!payment.isCancellable()) {
                log.warn("⚠️ Платеж нельзя отменить в текущем статусе: {} ({})",
                        paymentId, payment.getStatus());
                return false;
            }

            payment.updateStatus(PaymentStatus.CANCELLED, reason);
            paymentRepository.save(payment);

            log.info("✅ Платеж отменен: {}", paymentId);
            return true;

        } catch (Exception e) {
            log.error("❌ Ошибка при отмене платежа {}: {}", paymentId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Обработать истекшие платежи
     */
    @Transactional
    public void processExpiredPayments() {
        log.debug("🕐 Обработка истекших платежей...");

        List<PaymentStatus> activeStatuses = Arrays.asList(
                PaymentStatus.PENDING, PaymentStatus.PROCESSING, PaymentStatus.VERIFICATION_REQUIRED);

        List<PaymentEntity> expiredPayments = paymentRepository.findExpiredPayments(
                LocalDateTime.now(), activeStatuses);

        for (PaymentEntity payment : expiredPayments) {
            try {
                payment.updateStatus(PaymentStatus.EXPIRED, "Время платежа истекло");
                paymentRepository.save(payment);
                log.info("⌛ Платеж помечен как истекший: {}", payment.getPaymentId());
            } catch (Exception e) {
                log.error("❌ Ошибка при обработке истекшего платежа {}: {}",
                        payment.getPaymentId(), e.getMessage());
            }
        }

        if (!expiredPayments.isEmpty()) {
            log.info("✅ Обработано истекших платежей: {}", expiredPayments.size());
        }
    }

    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====

    private void validatePaymentData(Long userId, BigDecimal amount, String paymentMethod) {
        if (userId == null) {
            throw new IllegalArgumentException("ID пользователя не может быть null");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Сумма платежа должна быть положительной");
        }
        if (paymentMethod == null || paymentMethod.trim().isEmpty()) {
            throw new IllegalArgumentException("Метод оплаты не может быть пустым");
        }

        validatePaymentMethod(paymentMethod);
    }

    private void validatePaymentMethod(String paymentMethod) {
        // Используем Strategy Pattern для проверки поддержки метода
        if (!paymentStrategyFactory.isPaymentMethodSupported(paymentMethod)) {
            throw new IllegalArgumentException("Неподдерживаемый метод оплаты: " + paymentMethod +
                    ". Доступные методы: " + paymentStrategyFactory.getSupportedPaymentMethods());
        }
    }

    private String generatePaymentId() {
        return "PAY_" + System.currentTimeMillis() + "_" +
                ThreadLocalRandom.current().nextInt(1000, 9999);
    }
}