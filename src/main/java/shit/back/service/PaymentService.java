package shit.back.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shit.back.config.PaymentConfigurationProperties;
import shit.back.entity.PaymentEntity;
import shit.back.entity.PaymentStatus;
import shit.back.entity.UserBalanceEntity;
import shit.back.repository.PaymentJpaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Сервис для обработки платежей через различные платежные системы
 */
@Slf4j
@Service
public class PaymentService {

    @Autowired
    private PaymentJpaRepository paymentRepository;

    @Autowired
    private PaymentConfigurationProperties paymentConfig;

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
     * Обработать платеж и создать ссылку для оплаты
     */
    @Transactional
    public String processPayment(Long userId, BigDecimal amount, String paymentMethod) {
        log.info("🔄 Обработка платежа для пользователя {}: сумма={}, метод={}",
                userId, amount, paymentMethod);

        try {
            validatePaymentData(userId, amount, paymentMethod);

            // Создаем платеж
            PaymentEntity payment = createPayment(userId, amount, paymentMethod, null, "Пополнение баланса");

            // Создаем ссылку для оплаты в зависимости от метода
            String paymentUrl = createPaymentLink(payment);

            if (paymentUrl != null) {
                payment.setPaymentUrl(paymentUrl);
                payment.updateStatus(PaymentStatus.PENDING);
                paymentRepository.save(payment);

                log.info("✅ Ссылка для оплаты создана: {}", paymentUrl);
                return paymentUrl;
            } else {
                payment.updateStatus(PaymentStatus.FAILED, "Не удалось создать ссылку для оплаты");
                paymentRepository.save(payment);
                throw new RuntimeException("Не удалось создать ссылку для оплаты");
            }

        } catch (Exception e) {
            log.error("❌ Ошибка при обработке платежа для пользователя {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Ошибка при обработке платежа: " + e.getMessage());
        }
    }

    /**
     * Создать ссылку для оплаты
     */
    public String createPaymentLink(BigDecimal amount, String paymentMethod) {
        log.info("🔗 Создание ссылки для оплаты: сумма={}, метод={}", amount, paymentMethod);

        validatePaymentMethod(paymentMethod);

        // Создаем временный платеж для генерации ссылки
        String paymentId = generatePaymentId();
        PaymentEntity tempPayment = new PaymentEntity(paymentId, null, amount, "USD", paymentMethod);

        return createPaymentLink(tempPayment);
    }

    /**
     * Создать ссылку для оплаты на основе существующего платежа
     */
    private String createPaymentLink(PaymentEntity payment) {
        String paymentMethod = payment.getPaymentMethod().toLowerCase();

        switch (paymentMethod) {
            case "ton":
                return createTonPaymentLink(payment);
            case "yookassa":
                return createYooKassaPaymentLink(payment);
            case "qiwi":
                return createQiwiPaymentLink(payment);
            case "sberpay":
                return createSberPayPaymentLink(payment);
            default:
                log.warn("⚠️ Неподдерживаемый метод оплаты: {}", paymentMethod);
                return null;
        }
    }

    /**
     * Верифицировать callback от платежной системы
     */
    @Transactional
    public boolean verifyPaymentCallback(String paymentId, Map<String, String> params) {
        log.info("🔍 Верификация callback для платежа: {}", paymentId);

        try {
            Optional<PaymentEntity> paymentOpt = paymentRepository.findByPaymentId(paymentId);
            if (paymentOpt.isEmpty()) {
                log.warn("⚠️ Платеж не найден для callback: {}", paymentId);
                return false;
            }

            PaymentEntity payment = paymentOpt.get();
            String paymentMethod = payment.getPaymentMethod().toLowerCase();

            boolean isValid = false;
            switch (paymentMethod) {
                case "ton":
                    isValid = verifyTonCallback(payment, params);
                    break;
                case "yookassa":
                    isValid = verifyYooKassaCallback(payment, params);
                    break;
                case "qiwi":
                    isValid = verifyQiwiCallback(payment, params);
                    break;
                case "sberpay":
                    isValid = verifySberPayCallback(payment, params);
                    break;
                default:
                    log.warn("⚠️ Неподдерживаемый метод для верификации: {}", paymentMethod);
                    return false;
            }

            if (isValid) {
                // Обновляем статус платежа и пополняем баланс
                processSuccessfulPayment(payment);
                log.info("✅ Callback успешно обработан для платежа: {}", paymentId);
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
                balanceService.deposit(payment.getUserId(), payment.getAmount(),
                        payment.getPaymentMethod(),
                        "Пополнение через " + payment.getPaymentMethod() + " (ID: " + payment.getPaymentId() + ")");

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

    // ===== МЕТОДЫ ДЛЯ КОНКРЕТНЫХ ПЛАТЕЖНЫХ СИСТЕМ =====

    private String createTonPaymentLink(PaymentEntity payment) {
        if (!paymentConfig.getTon().getEnabled()) {
            log.warn("⚠️ TON Wallet отключен в конфигурации");
            return null;
        }

        // TODO: Реальная интеграция с TON Wallet API
        log.info("🚧 TON Wallet: Создание ссылки для платежа {}", payment.getPaymentId());

        // Заглушка - возвращаем демо-ссылку
        return String.format("https://wallet.ton.org/pay?amount=%s&payment_id=%s",
                payment.getAmount(), payment.getPaymentId());
    }

    private String createYooKassaPaymentLink(PaymentEntity payment) {
        if (!paymentConfig.getYookassa().getEnabled()) {
            log.warn("⚠️ YooKassa отключена в конфигурации");
            return null;
        }

        // TODO: Реальная интеграция с YooKassa API
        log.info("🚧 YooKassa: Создание ссылки для платежа {}", payment.getPaymentId());

        // Заглушка - возвращаем демо-ссылку
        return String.format("https://yookassa.ru/checkout?amount=%s&payment_id=%s",
                payment.getAmount(), payment.getPaymentId());
    }

    private String createQiwiPaymentLink(PaymentEntity payment) {
        if (!paymentConfig.getQiwi().getEnabled()) {
            log.warn("⚠️ Qiwi отключен в конфигурации");
            return null;
        }

        // TODO: Реальная интеграция с Qiwi API
        log.info("🚧 Qiwi: Создание ссылки для платежа {}", payment.getPaymentId());

        // Заглушка - возвращаем демо-ссылку
        return String.format("https://oplata.qiwi.com/create?amount=%s&payment_id=%s",
                payment.getAmount(), payment.getPaymentId());
    }

    private String createSberPayPaymentLink(PaymentEntity payment) {
        if (!paymentConfig.getSberpay().getEnabled()) {
            log.warn("⚠️ SberPay отключен в конфигурации");
            return null;
        }

        // TODO: Реальная интеграция с SberPay API
        log.info("🚧 SberPay: Создание ссылки для платежа {}", payment.getPaymentId());

        // Заглушка - возвращаем демо-ссылку
        return String.format(
                "https://securepayments.sberbank.ru/payment/merchants/%s/payment_pages?amount=%s&payment_id=%s",
                paymentConfig.getSberpay().getMerchantId(), payment.getAmount(), payment.getPaymentId());
    }

    // ===== МЕТОДЫ ВЕРИФИКАЦИИ CALLBACK'ОВ =====

    private boolean verifyTonCallback(PaymentEntity payment, Map<String, String> params) {
        // TODO: Реальная верификация подписи TON
        log.info("🚧 TON: Верификация callback для платежа {}", payment.getPaymentId());
        return true; // Заглушка
    }

    private boolean verifyYooKassaCallback(PaymentEntity payment, Map<String, String> params) {
        // TODO: Реальная верификация подписи YooKassa
        log.info("🚧 YooKassa: Верификация callback для платежа {}", payment.getPaymentId());
        return true; // Заглушка
    }

    private boolean verifyQiwiCallback(PaymentEntity payment, Map<String, String> params) {
        // TODO: Реальная верификация подписи Qiwi
        log.info("🚧 Qiwi: Верификация callback для платежа {}", payment.getPaymentId());
        return true; // Заглушка
    }

    private boolean verifySberPayCallback(PaymentEntity payment, Map<String, String> params) {
        // TODO: Реальная верификация подписи SberPay
        log.info("🚧 SberPay: Верификация callback для платежа {}", payment.getPaymentId());
        return true; // Заглушка
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
        String[] enabledMethods = paymentConfig.getEnabledPaymentMethods();
        boolean isSupported = Arrays.stream(enabledMethods)
                .anyMatch(method -> method.equalsIgnoreCase(paymentMethod));

        if (!isSupported) {
            throw new IllegalArgumentException("Неподдерживаемый метод оплаты: " + paymentMethod);
        }
    }

    private String generatePaymentId() {
        return "PAY_" + System.currentTimeMillis() + "_" +
                ThreadLocalRandom.current().nextInt(1000, 9999);
    }
}