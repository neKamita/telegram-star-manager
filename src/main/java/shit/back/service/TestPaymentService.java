package shit.back.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shit.back.entity.PaymentEntity;
import shit.back.entity.PaymentStatus;
import shit.back.repository.PaymentJpaRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Сервис для обработки тестовых платежей в dev режиме
 * 
 * Позволяет имитировать весь флоу покупки без реальных платежных интеграций
 */
@Service
@ConditionalOnProperty(name = "test.payment.enabled", havingValue = "true")
public class TestPaymentService {

    private static final Logger log = LoggerFactory.getLogger(TestPaymentService.class);

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentJpaRepository paymentRepository;

    @Autowired(required = false)
    private TelegramBotService telegramBotService;

    @Autowired
    private TaskScheduler taskScheduler;

    @Value("${test.payment.auto-complete.enabled:true}")
    private boolean autoCompleteEnabled;

    @Value("${test.payment.auto-complete.delay-seconds:3}")
    private int autoCompleteDelaySeconds;

    @Value("${test.payment.logging.enabled:true}")
    private boolean loggingEnabled;

    /**
     * Создать тестовый платеж
     */
    @Transactional
    public PaymentEntity createTestPayment(Long userId, BigDecimal amount, String description) {
        if (loggingEnabled) {
            log.info("🧪 ТЕСТ: Создание тестового платежа для пользователя {}: сумма={}, описание={}",
                    userId, amount, description);
        }

        // Создаем платеж через обычный PaymentService
        PaymentEntity payment = paymentService.createPayment(userId, amount, "TEST", null, description);

        // Обновляем URL платежа через setExternalData
        String testUrl = "test://payment/" + payment.getPaymentId() + "?test_mode=true";
        payment.setExternalData("TEST_EXT_" + System.currentTimeMillis(), testUrl);
        payment.updateStatus(PaymentStatus.PENDING);
        payment = paymentRepository.save(payment);

        if (loggingEnabled) {
            log.info("✅ ТЕСТ: Тестовый платеж создан: ID={}", payment.getPaymentId());
        }

        // Автоматически завершаем платеж, если включено
        if (autoCompleteEnabled) {
            scheduleAutoCompletion(payment);
        }

        return payment;
    }

    /**
     * Немедленное завершение тестового платежа
     */
    @Transactional
    public boolean completeTestPaymentImmediately(String paymentId) {
        if (loggingEnabled) {
            log.info("🧪 ТЕСТ: Немедленное завершение тестового платежа: {}", paymentId);
        }

        try {
            Optional<PaymentEntity> paymentOpt = paymentRepository.findByPaymentId(paymentId);
            if (paymentOpt.isEmpty()) {
                log.warn("⚠️ ТЕСТ: Платеж не найден: {}", paymentId);
                return false;
            }

            PaymentEntity payment = paymentOpt.get();
            return processTestPaymentCompletion(payment);

        } catch (Exception e) {
            log.error("❌ ТЕСТ: Ошибка при немедленном завершении тестового платежа {}: {}",
                    paymentId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Запланировать автоматическое завершение платежа
     */
    private void scheduleAutoCompletion(PaymentEntity payment) {
        if (loggingEnabled) {
            log.info("⏰ ТЕСТ: Запланировано автозавершение платежа через {} секунд",
                    autoCompleteDelaySeconds);
        }

        taskScheduler.schedule(() -> {
            try {
                if (loggingEnabled) {
                    log.info("🎯 ТЕСТ: Обработка успешного завершения платежа");
                }
                processSuccessfulCompletion(payment.getPaymentId());
            } catch (Exception e) {
                log.error("❌ ТЕСТ: Ошибка при автозавершении платежа {}: {}",
                        payment.getPaymentId(), e.getMessage(), e);
            }
        }, Instant.now().plusSeconds(autoCompleteDelaySeconds));
    }

    /**
     * Обработка успешного завершения платежа по ID
     */
    @Transactional
    public void processSuccessfulCompletion(String paymentId) {
        try {
            Optional<PaymentEntity> paymentOpt = paymentRepository.findByPaymentId(paymentId);
            if (paymentOpt.isEmpty()) {
                log.warn("⚠️ ТЕСТ: Платеж не найден для автозавершения: {}", paymentId);
                return;
            }

            PaymentEntity payment = paymentOpt.get();
            processTestPaymentCompletion(payment);

        } catch (Exception e) {
            log.error("❌ ТЕСТ: Ошибка при обработке успешного завершения платежа {}: {}",
                    paymentId, e.getMessage(), e);
        }
    }

    /**
     * Обработка завершения тестового платежа
     */
    @Transactional
    private boolean processTestPaymentCompletion(PaymentEntity payment) {
        try {
            // Создаем параметры для callback
            Map<String, String> testParams = createTestCallbackParams(payment);

            if (loggingEnabled) {
                log.info("🎯 ТЕСТ: Обработка успешного завершения платежа");
            }

            // Получаем актуальный платеж из БД
            Optional<PaymentEntity> paymentOpt = paymentRepository.findByPaymentId(payment.getPaymentId());
            if (paymentOpt.isPresent()) {
                PaymentEntity actualPayment = paymentOpt.get();
                boolean success = paymentService.verifyPaymentCallback(actualPayment.getPaymentId(), testParams);

                if (success) {
                    if (loggingEnabled) {
                        log.info("✅ ТЕСТ: Платеж успешно завершен и баланс пополнен");
                    }

                    // Автоматически перенаправляем пользователя к балансу после успешного тестового
                    // платежа
                    sendTestPaymentCompletionMessage(actualPayment.getUserId(), actualPayment.getAmount());
                }

                return success;
            }

            return false;

        } catch (Exception e) {
            log.error("❌ ТЕСТ: Ошибка при завершении тестового платежа: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Создание параметров для тестового callback
     */
    private Map<String, String> createTestCallbackParams(PaymentEntity payment) {
        Map<String, String> params = new HashMap<>();

        // Получаем актуальные данные из БД
        Optional<PaymentEntity> paymentOpt = paymentRepository.findByPaymentId(payment.getPaymentId());
        if (paymentOpt.isPresent()) {
            PaymentEntity actualPayment = paymentOpt.get();
            params.put("payment_id", actualPayment.getPaymentId());
            params.put("status", "completed");
            params.put("amount", actualPayment.getAmount().toString());
            params.put("currency", actualPayment.getCurrency());
            params.put("test_mode", "true");
            params.put("transaction_id", "TEST_TXN_" + System.currentTimeMillis());
        }

        return params;
    }

    /**
     * Проверка, включен ли тестовый режим
     */
    public boolean isTestModeEnabled() {
        return true; // Класс создается только если test.payment.enabled=true
    }

    /**
     * Получение конфигурации тестового режима
     */
    public Map<String, Object> getTestModeConfiguration() {
        Map<String, Object> config = new HashMap<>();
        config.put("enabled", true);
        config.put("auto_complete_enabled", autoCompleteEnabled);
        config.put("auto_complete_delay_seconds", autoCompleteDelaySeconds);
        config.put("logging_enabled", loggingEnabled);

        return config;
    }

    /**
     * Отправляет сообщение пользователю о завершении тестового платежа и
     * перенаправляет к балансу
     */
    @Async
    private void sendTestPaymentCompletionMessage(Long userId, BigDecimal amount) {
        try {
            String message = String.format("""
                    ✅ <b>Тестовый платеж успешно завершен!</b>

                    💰 <b>Сумма:</b> %.2f ₽
                    💳 <b>Баланс пополнен</b>
                    🧪 <b>Режим:</b> Тестовый

                    🎉 <i>Средства зачислены на ваш баланс!</i>

                    👇 Перейдите к балансу для просмотра обновленного состояния:
                    """, amount);

            // Создаем клавиатуру для перехода к балансу
            org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup keyboard = createTestPaymentCompletionKeyboard();

            // Отправляем сообщение через доступный сервис
            if (telegramBotService != null) {
                telegramBotService.sendMessageWithKeyboard(userId, message, keyboard);
                if (loggingEnabled) {
                    log.info("🧪 ТЕСТ: Отправлено уведомление о завершении платежа пользователю {}", userId);
                }
            } else {
                if (loggingEnabled) {
                    log.warn("⚠️ ТЕСТ: TelegramBotService недоступен, не удалось отправить уведомление пользователю {}",
                            userId);
                }
            }

        } catch (Exception e) {
            log.error("❌ ТЕСТ: Ошибка при отправке уведомления о завершении платежа пользователю {}: {}",
                    userId, e.getMessage(), e);
        }
    }

    /**
     * Создает клавиатуру для сообщения о завершении тестового платежа
     */
    private org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup createTestPaymentCompletionKeyboard() {
        org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup keyboard = new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
        java.util.List<java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> rows = new java.util.ArrayList<>();

        // Кнопка перехода к балансу
        java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row1 = new java.util.ArrayList<>();
        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton balanceButton = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton();
        balanceButton.setText("💰 Перейти к балансу");
        balanceButton.setCallbackData("show_balance");
        row1.add(balanceButton);
        rows.add(row1);

        // Кнопка покупки звезд
        java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row2 = new java.util.ArrayList<>();
        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton buyButton = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton();
        buyButton.setText("⭐ Купить звезды");
        buyButton.setCallbackData("buy_stars");
        row2.add(buyButton);
        rows.add(row2);

        // Кнопка главного меню
        java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row3 = new java.util.ArrayList<>();
        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton mainButton = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton();
        mainButton.setText("🏠 Главное меню");
        mainButton.setCallbackData("back_to_main");
        row3.add(mainButton);
        rows.add(row3);

        keyboard.setKeyboard(rows);
        return keyboard;
    }
}