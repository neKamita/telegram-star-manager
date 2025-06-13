package shit.back.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import shit.back.config.PaymentConfigurationProperties;
import shit.back.entity.PaymentEntity;
import shit.back.entity.PaymentStatus;
import shit.back.service.PaymentService;
import shit.back.security.SecurityValidator;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Контроллер для обработки callback'ов от платежных систем
 * 
 * Обрабатывает уведомления о статусе платежей от различных платежных систем
 * и автоматически обновляет баланс пользователей
 */
@Slf4j
@RestController
@RequestMapping("/api/payment/callback")
public class PaymentCallbackController {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentConfigurationProperties paymentConfig;

    @Autowired
    private SecurityValidator securityValidator;

    /**
     * Callback от TON Wallet
     */
    @PostMapping("/ton")
    public ResponseEntity<Map<String, Object>> handleTonCallback(
            @RequestBody Map<String, Object> payload,
            @RequestHeader Map<String, String> headers,
            HttpServletRequest request) {

        log.info("💎 Получен callback от TON Wallet: {}", payload);

        try {
            // Проверяем, что TON включен
            if (!paymentConfig.getTon().getEnabled()) {
                log.warn("⚠️ TON Wallet отключен, но получен callback");
                return createErrorResponse("TON Wallet отключен", HttpStatus.BAD_REQUEST);
            }

            // Извлекаем данные из payload
            Map<String, String> params = extractTonParams(payload);
            String paymentId = params.get("payment_id");

            if (paymentId == null || paymentId.isEmpty()) {
                log.warn("⚠️ Не найден payment_id в TON callback");
                return createErrorResponse("Отсутствует payment_id", HttpStatus.BAD_REQUEST);
            }

            // Верифицируем подпись
            if (!verifyTonSignature(params, headers)) {
                log.warn("❌ Неверная подпись TON callback для платежа: {}", paymentId);
                return createErrorResponse("Неверная подпись", HttpStatus.UNAUTHORIZED);
            }

            // Обрабатываем callback
            boolean success = paymentService.verifyPaymentCallback(paymentId, params);

            if (success) {
                log.info("✅ TON callback успешно обработан для платежа: {}", paymentId);
                return createSuccessResponse("OK");
            } else {
                log.warn("❌ Ошибка при обработке TON callback для платежа: {}", paymentId);
                return createErrorResponse("Ошибка обработки", HttpStatus.INTERNAL_SERVER_ERROR);
            }

        } catch (Exception e) {
            log.error("❌ Исключение при обработке TON callback: {}", e.getMessage(), e);
            return createErrorResponse("Внутренняя ошибка сервера", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Callback от YooKassa
     */
    @PostMapping("/yookassa")
    public ResponseEntity<Map<String, Object>> handleYooKassaCallback(
            @RequestBody Map<String, Object> payload,
            @RequestHeader Map<String, String> headers,
            HttpServletRequest request) {

        log.info("💳 Получен callback от YooKassa: {}", payload);

        try {
            // Проверяем, что YooKassa включена
            if (!paymentConfig.getYookassa().getEnabled()) {
                log.warn("⚠️ YooKassa отключена, но получен callback");
                return createErrorResponse("YooKassa отключена", HttpStatus.BAD_REQUEST);
            }

            // Извлекаем данные из payload
            Map<String, String> params = extractYooKassaParams(payload);
            String paymentId = params.get("payment_id");

            if (paymentId == null || paymentId.isEmpty()) {
                log.warn("⚠️ Не найден payment_id в YooKassa callback");
                return createErrorResponse("Отсутствует payment_id", HttpStatus.BAD_REQUEST);
            }

            // Верифицируем подпись
            if (!verifyYooKassaSignature(params, headers)) {
                log.warn("❌ Неверная подпись YooKassa callback для платежа: {}", paymentId);
                return createErrorResponse("Неверная подпись", HttpStatus.UNAUTHORIZED);
            }

            // Обрабатываем callback
            boolean success = paymentService.verifyPaymentCallback(paymentId, params);

            if (success) {
                log.info("✅ YooKassa callback успешно обработан для платежа: {}", paymentId);
                return createSuccessResponse("OK");
            } else {
                log.warn("❌ Ошибка при обработке YooKassa callback для платежа: {}", paymentId);
                return createErrorResponse("Ошибка обработки", HttpStatus.INTERNAL_SERVER_ERROR);
            }

        } catch (Exception e) {
            log.error("❌ Исключение при обработке YooKassa callback: {}", e.getMessage(), e);
            return createErrorResponse("Внутренняя ошибка сервера", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Callback от Qiwi
     */
    @PostMapping("/qiwi")
    public ResponseEntity<Map<String, Object>> handleQiwiCallback(
            @RequestBody Map<String, Object> payload,
            @RequestHeader Map<String, String> headers,
            HttpServletRequest request) {

        log.info("🥝 Получен callback от Qiwi: {}", payload);

        try {
            // Проверяем, что Qiwi включен
            if (!paymentConfig.getQiwi().getEnabled()) {
                log.warn("⚠️ Qiwi отключен, но получен callback");
                return createErrorResponse("Qiwi отключен", HttpStatus.BAD_REQUEST);
            }

            // Извлекаем данные из payload
            Map<String, String> params = extractQiwiParams(payload);
            String paymentId = params.get("payment_id");

            if (paymentId == null || paymentId.isEmpty()) {
                log.warn("⚠️ Не найден payment_id в Qiwi callback");
                return createErrorResponse("Отсутствует payment_id", HttpStatus.BAD_REQUEST);
            }

            // Верифицируем подпись
            if (!verifyQiwiSignature(params, headers)) {
                log.warn("❌ Неверная подпись Qiwi callback для платежа: {}", paymentId);
                return createErrorResponse("Неверная подпись", HttpStatus.UNAUTHORIZED);
            }

            // Обрабатываем callback
            boolean success = paymentService.verifyPaymentCallback(paymentId, params);

            if (success) {
                log.info("✅ Qiwi callback успешно обработан для платежа: {}", paymentId);
                return createSuccessResponse("OK");
            } else {
                log.warn("❌ Ошибка при обработке Qiwi callback для платежа: {}", paymentId);
                return createErrorResponse("Ошибка обработки", HttpStatus.INTERNAL_SERVER_ERROR);
            }

        } catch (Exception e) {
            log.error("❌ Исключение при обработке Qiwi callback: {}", e.getMessage(), e);
            return createErrorResponse("Внутренняя ошибка сервера", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Callback от SberPay
     */
    @PostMapping("/sberpay")
    public ResponseEntity<Map<String, Object>> handleSberPayCallback(
            @RequestBody Map<String, Object> payload,
            @RequestHeader Map<String, String> headers,
            HttpServletRequest request) {

        log.info("🏦 Получен callback от SberPay: {}", payload);

        try {
            // Проверяем, что SberPay включен
            if (!paymentConfig.getSberpay().getEnabled()) {
                log.warn("⚠️ SberPay отключен, но получен callback");
                return createErrorResponse("SberPay отключен", HttpStatus.BAD_REQUEST);
            }

            // Извлекаем данные из payload
            Map<String, String> params = extractSberPayParams(payload);
            String paymentId = params.get("payment_id");

            if (paymentId == null || paymentId.isEmpty()) {
                log.warn("⚠️ Не найден payment_id в SberPay callback");
                return createErrorResponse("Отсутствует payment_id", HttpStatus.BAD_REQUEST);
            }

            // Верифицируем подпись
            if (!verifySberPaySignature(params, headers)) {
                log.warn("❌ Неверная подпись SberPay callback для платежа: {}", paymentId);
                return createErrorResponse("Неверная подпись", HttpStatus.UNAUTHORIZED);
            }

            // Обрабатываем callback
            boolean success = paymentService.verifyPaymentCallback(paymentId, params);

            if (success) {
                log.info("✅ SberPay callback успешно обработан для платежа: {}", paymentId);
                return createSuccessResponse("OK");
            } else {
                log.warn("❌ Ошибка при обработке SberPay callback для платежа: {}", paymentId);
                return createErrorResponse("Ошибка обработки", HttpStatus.INTERNAL_SERVER_ERROR);
            }

        } catch (Exception e) {
            log.error("❌ Исключение при обработке SberPay callback: {}", e.getMessage(), e);
            return createErrorResponse("Внутренняя ошибка сервера", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Универсальный endpoint для проверки статуса платежа
     */
    @GetMapping("/status/{paymentId}")
    public ResponseEntity<Map<String, Object>> getPaymentStatus(@PathVariable String paymentId) {
        log.info("🔍 Запрос статуса платежа: {}", paymentId);

        try {
            // Валидация payment ID
            if (paymentId == null || paymentId.trim().isEmpty() || paymentId.length() > 50) {
                return createErrorResponse("Некорректный ID платежа", HttpStatus.BAD_REQUEST);
            }

            Optional<PaymentEntity> paymentOpt = paymentService.getPayment(paymentId);

            if (paymentOpt.isEmpty()) {
                return createErrorResponse("Платеж не найден", HttpStatus.NOT_FOUND);
            }

            PaymentEntity payment = paymentOpt.get();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("payment_id", payment.getPaymentId());
            response.put("status", payment.getStatus().name());
            response.put("status_display", payment.getFormattedStatus());
            response.put("amount", payment.getFormattedAmount());
            response.put("payment_method", payment.getPaymentMethod());
            response.put("created_at", payment.getCreatedAt());
            response.put("updated_at", payment.getUpdatedAt());

            if (payment.getCompletedAt() != null) {
                response.put("completed_at", payment.getCompletedAt());
            }

            if (payment.getErrorMessage() != null) {
                response.put("error_message", payment.getErrorMessage());
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Ошибка при получении статуса платежа {}: {}", paymentId, e.getMessage(), e);
            return createErrorResponse("Внутренняя ошибка сервера", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // ===== МЕТОДЫ ИЗВЛЕЧЕНИЯ ПАРАМЕТРОВ =====

    private Map<String, String> extractTonParams(Map<String, Object> payload) {
        Map<String, String> params = new HashMap<>();

        // TODO: Реальная реализация для TON Wallet
        // Пока заглушка для демонстрации
        params.put("payment_id", String.valueOf(payload.getOrDefault("payment_id", "")));
        params.put("status", String.valueOf(payload.getOrDefault("status", "completed")));
        params.put("amount", String.valueOf(payload.getOrDefault("amount", "0")));

        return params;
    }

    private Map<String, String> extractYooKassaParams(Map<String, Object> payload) {
        Map<String, String> params = new HashMap<>();

        // TODO: Реальная реализация для YooKassa
        // Пока заглушка для демонстрации
        params.put("payment_id", String.valueOf(payload.getOrDefault("payment_id", "")));
        params.put("status", String.valueOf(payload.getOrDefault("status", "succeeded")));
        params.put("amount", String.valueOf(payload.getOrDefault("amount", "0")));

        return params;
    }

    private Map<String, String> extractQiwiParams(Map<String, Object> payload) {
        Map<String, String> params = new HashMap<>();

        // TODO: Реальная реализация для Qiwi
        // Пока заглушка для демонстрации
        params.put("payment_id", String.valueOf(payload.getOrDefault("payment_id", "")));
        params.put("status", String.valueOf(payload.getOrDefault("status", "PAID")));
        params.put("amount", String.valueOf(payload.getOrDefault("amount", "0")));

        return params;
    }

    private Map<String, String> extractSberPayParams(Map<String, Object> payload) {
        Map<String, String> params = new HashMap<>();

        // TODO: Реальная реализация для SberPay
        // Пока заглушка для демонстрации
        params.put("payment_id", String.valueOf(payload.getOrDefault("payment_id", "")));
        params.put("status", String.valueOf(payload.getOrDefault("status", "DEPOSITED")));
        params.put("amount", String.valueOf(payload.getOrDefault("amount", "0")));

        return params;
    }

    // ===== МЕТОДЫ ВЕРИФИКАЦИИ ПОДПИСЕЙ =====

    private boolean verifyTonSignature(Map<String, String> params, Map<String, String> headers) {
        // TODO: Реальная верификация подписи TON
        log.info("🚧 TON: Верификация подписи (заглушка)");
        return true; // Заглушка
    }

    private boolean verifyYooKassaSignature(Map<String, String> params, Map<String, String> headers) {
        // TODO: Реальная верификация подписи YooKassa
        log.info("🚧 YooKassa: Верификация подписи (заглушка)");
        return true; // Заглушка
    }

    private boolean verifyQiwiSignature(Map<String, String> params, Map<String, String> headers) {
        // TODO: Реальная верификация подписи Qiwi
        log.info("🚧 Qiwi: Верификация подписи (заглушка)");
        return true; // Заглушка
    }

    private boolean verifySberPaySignature(Map<String, String> params, Map<String, String> headers) {
        // TODO: Реальная верификация подписи SberPay
        log.info("🚧 SberPay: Верификация подписи (заглушка)");
        return true; // Заглушка
    }

    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====

    private ResponseEntity<Map<String, Object>> createSuccessResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<Map<String, Object>> createErrorResponse(String message, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        return ResponseEntity.status(status).body(response);
    }
}