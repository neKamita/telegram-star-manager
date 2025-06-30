package shit.back.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import shit.back.config.PaymentConfigurationProperties;
import shit.back.entity.PaymentEntity;
import shit.back.entity.PaymentStatus;
import shit.back.service.PaymentService;
import shit.back.service.TestPaymentService;
import shit.back.security.SecurityValidator;

import jakarta.servlet.http.HttpServletRequest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    @Autowired(required = false)
    private TestPaymentService testPaymentService;

    @Value("${TON_WEBHOOK_SECRET:}")
    private String tonWebhookSecret;

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
     * Callback от Telegram Fragment API
     */
    @PostMapping("/fragment")
    public ResponseEntity<Map<String, Object>> handleFragmentCallback(
            @RequestBody Map<String, Object> payload,
            @RequestHeader Map<String, String> headers,
            HttpServletRequest request) {

        log.info("⭐ Получен callback от Telegram Fragment: {}", payload);

        try {
            // Проверяем, что Fragment включен
            if (!paymentConfig.getFragment().getEnabled()) {
                log.warn("⚠️ Telegram Fragment отключен, но получен callback");
                return createErrorResponse("Telegram Fragment отключен", HttpStatus.BAD_REQUEST);
            }

            // Извлекаем данные из payload
            Map<String, String> params = extractFragmentParams(payload);
            String paymentId = params.get("payment_id");

            if (paymentId == null || paymentId.isEmpty()) {
                log.warn("⚠️ Не найден payment_id в Fragment callback");
                return createErrorResponse("Отсутствует payment_id", HttpStatus.BAD_REQUEST);
            }

            // Верифицируем подпись
            if (!verifyFragmentSignature(params, headers)) {
                log.warn("❌ Неверная подпись Fragment callback для платежа: {}", paymentId);
                return createErrorResponse("Неверная подпись", HttpStatus.UNAUTHORIZED);
            }

            // Обрабатываем callback
            boolean success = paymentService.verifyPaymentCallback(paymentId, params);

            if (success) {
                log.info("✅ Fragment callback успешно обработан для платежа: {}", paymentId);
                return createSuccessResponse("OK");
            } else {
                log.warn("❌ Ошибка при обработке Fragment callback для платежа: {}", paymentId);
                return createErrorResponse("Ошибка обработки", HttpStatus.INTERNAL_SERVER_ERROR);
            }

        } catch (Exception e) {
            log.error("❌ Исключение при обработке Fragment callback: {}", e.getMessage(), e);
            return createErrorResponse("Внутренняя ошибка сервера", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Callback от UZS платежной системы
     */
    @PostMapping("/uzs")
    public ResponseEntity<Map<String, Object>> handleUzsCallback(
            @RequestBody Map<String, Object> payload,
            @RequestHeader Map<String, String> headers,
            HttpServletRequest request) {

        log.info("🏧 Получен callback от UZS платежной системы: {}", payload);

        try {
            // Проверяем, что UZS включена
            if (!paymentConfig.getUzsPayment().getEnabled()) {
                log.warn("⚠️ UZS платежная система отключена, но получен callback");
                return createErrorResponse("UZS платежная система отключена", HttpStatus.BAD_REQUEST);
            }

            // Извлекаем данные из payload
            Map<String, String> params = extractUzsParams(payload);
            String paymentId = params.get("payment_id");

            if (paymentId == null || paymentId.isEmpty()) {
                log.warn("⚠️ Не найден payment_id в UZS callback");
                return createErrorResponse("Отсутствует payment_id", HttpStatus.BAD_REQUEST);
            }

            // Верифицируем подпись
            if (!verifyUzsSignature(params, headers)) {
                log.warn("❌ Неверная подпись UZS callback для платежа: {}", paymentId);
                return createErrorResponse("Неверная подпись", HttpStatus.UNAUTHORIZED);
            }

            // Обрабатываем callback
            boolean success = paymentService.verifyPaymentCallback(paymentId, params);

            if (success) {
                log.info("✅ UZS callback успешно обработан для платежа: {}", paymentId);
                return createSuccessResponse("OK");
            } else {
                log.warn("❌ Ошибка при обработке UZS callback для платежа: {}", paymentId);
                return createErrorResponse("Ошибка обработки", HttpStatus.INTERNAL_SERVER_ERROR);
            }

        } catch (Exception e) {
            log.error("❌ Исключение при обработке UZS callback: {}", e.getMessage(), e);
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

    private Map<String, String> extractFragmentParams(Map<String, Object> payload) {
        Map<String, String> params = new HashMap<>();

        // Извлечение параметров из Telegram Fragment API
        params.put("payment_id", String.valueOf(payload.getOrDefault("payment_id", "")));
        params.put("status", String.valueOf(payload.getOrDefault("status", "completed")));
        params.put("amount", String.valueOf(payload.getOrDefault("amount", "0")));
        params.put("currency", String.valueOf(payload.getOrDefault("currency", "XTR")));
        params.put("stars_amount", String.valueOf(payload.getOrDefault("stars_amount", "0")));

        return params;
    }

    private Map<String, String> extractUzsParams(Map<String, Object> payload) {
        Map<String, String> params = new HashMap<>();

        // Извлечение параметров из UZS платежной системы
        params.put("payment_id", String.valueOf(payload.getOrDefault("payment_id", "")));
        params.put("status", String.valueOf(payload.getOrDefault("status", "SUCCESS")));
        params.put("amount", String.valueOf(payload.getOrDefault("amount", "0")));
        params.put("currency", String.valueOf(payload.getOrDefault("currency", "UZS")));
        params.put("transaction_id", String.valueOf(payload.getOrDefault("transaction_id", "")));

        return params;
    }

    // ===== МЕТОДЫ ВЕРИФИКАЦИИ ПОДПИСЕЙ =====

    /**
     * Верификация HMAC-SHA256 подписи для TON платежей
     *
     * @param params  параметры callback'а от TON
     * @param headers заголовки HTTP запроса
     * @return true если подпись корректна, false в противном случае
     */
    private boolean verifyTonSignature(Map<String, String> params, Map<String, String> headers) {
        try {
            // Получаем секретный ключ из переменных окружения
            if (tonWebhookSecret == null || tonWebhookSecret.trim().isEmpty()) {
                log.error("🔒 TON webhook secret не настроен в переменных окружения");
                return false;
            }

            // Извлекаем подпись из заголовков
            String providedSignature = headers.get("x-signature");
            if (providedSignature == null) {
                providedSignature = headers.get("X-Signature");
            }
            if (providedSignature == null) {
                providedSignature = headers.get("signature");
            }

            if (providedSignature == null || providedSignature.trim().isEmpty()) {
                log.warn("⚠️ TON callback: Отсутствует подпись в заголовках");
                return false;
            }

            // Удаляем префикс "sha256=" если присутствует
            if (providedSignature.startsWith("sha256=")) {
                providedSignature = providedSignature.substring(7);
            }

            // Строим строку для подписи из параметров
            String signatureString = buildTonSignatureString(params);
            log.debug("🔍 TON signature string: {}", signatureString);

            // Вычисляем HMAC-SHA256
            String computedSignature = computeHmacSha256(signatureString, tonWebhookSecret.trim());

            // Безопасное сравнение подписей (constant-time)
            boolean isValid = constantTimeEquals(providedSignature, computedSignature);

            if (isValid) {
                log.info("✅ TON webhook signature успешно верифицирована для платежа: {}",
                        params.get("payment_id"));
            } else {
                log.warn("❌ TON webhook signature не прошла верификацию. Payment ID: {}, " +
                        "Expected: {}, Provided: {}",
                        params.get("payment_id"), computedSignature, providedSignature);
            }

            return isValid;

        } catch (Exception e) {
            log.error("💥 Ошибка при верификации TON подписи для платежа {}: {}",
                    params.get("payment_id"), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Строит строку для подписи из параметров TON callback'а
     * согласно спецификации TON Wallet API
     */
    private String buildTonSignatureString(Map<String, String> params) {
        // Сортируем параметры по ключу и объединяем в строку
        // Формат: key1=value1&key2=value2&...
        return params.entrySet()
                .stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    /**
     * Вычисляет HMAC-SHA256 подпись
     *
     * @param data данные для подписи
     * @param key  секретный ключ
     * @return HEX представление HMAC-SHA256
     */
    private String computeHmacSha256(String data, String key) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);

        byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

        // Конвертируем в HEX
        StringBuilder hexString = new StringBuilder();
        for (byte b : hmacBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }

        return hexString.toString();
    }

    /**
     * Безопасное сравнение строк для предотвращения timing attacks
     *
     * @param a первая строка
     * @param b вторая строка
     * @return true если строки идентичны
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }

        if (a.length() != b.length()) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }

        return result == 0;
    }

    private boolean verifyYooKassaSignature(Map<String, String> params, Map<String, String> headers) {
        // TODO: Реальная верификация подписи YooKassa
        log.info("🚧 YooKassa: Верификация подписи (заглушка)");
        return true; // Заглушка
    }

    private boolean verifyFragmentSignature(Map<String, String> params, Map<String, String> headers) {
        try {
            String fragmentToken = paymentConfig.getFragment().getToken();
            if (fragmentToken == null || fragmentToken.trim().isEmpty()) {
                log.error("🔒 Fragment API token не настроен");
                return false;
            }

            String providedSignature = headers.get("x-telegram-bot-api-secret-token");
            if (providedSignature == null) {
                providedSignature = headers.get("X-Telegram-Bot-Api-Secret-Token");
            }

            if (providedSignature == null || providedSignature.trim().isEmpty()) {
                log.warn("⚠️ Fragment callback: Отсутствует подпись в заголовках");
                return false;
            }

            // Telegram Fragment использует простое сравнение токенов
            boolean isValid = constantTimeEquals(providedSignature, fragmentToken);

            if (isValid) {
                log.info("✅ Fragment signature успешно верифицирована для платежа: {}", params.get("payment_id"));
            } else {
                log.warn("❌ Fragment signature не прошла верификацию для платежа: {}", params.get("payment_id"));
            }

            return isValid;

        } catch (Exception e) {
            log.error("💥 Ошибка при верификации Fragment подписи: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean verifyUzsSignature(Map<String, String> params, Map<String, String> headers) {
        try {
            String uzsSecret = paymentConfig.getUzsPayment().getSecretKey();
            if (uzsSecret == null || uzsSecret.trim().isEmpty()) {
                log.error("🔒 UZS secret key не настроен");
                return false;
            }

            String providedSignature = headers.get("x-signature");
            if (providedSignature == null) {
                providedSignature = headers.get("signature");
            }

            if (providedSignature == null || providedSignature.trim().isEmpty()) {
                log.warn("⚠️ UZS callback: Отсутствует подпись в заголовках");
                return false;
            }

            // Строим строку для подписи из параметров UZS
            String signatureString = buildUzsSignatureString(params);
            String computedSignature = computeHmacSha256(signatureString, uzsSecret);

            boolean isValid = constantTimeEquals(providedSignature, computedSignature);

            if (isValid) {
                log.info("✅ UZS signature успешно верифицирована для платежа: {}", params.get("payment_id"));
            } else {
                log.warn("❌ UZS signature не прошла верификацию для платежа: {}", params.get("payment_id"));
            }

            return isValid;

        } catch (Exception e) {
            log.error("💥 Ошибка при верификации UZS подписи: {}", e.getMessage(), e);
            return false;
        }
    }

    private String buildUzsSignatureString(Map<String, String> params) {
        return params.entrySet()
                .stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
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

    // ===== ТЕСТОВЫЕ ENDPOINTS =====

    /**
     * Тестовый endpoint для немедленного завершения платежа (только в dev режиме)
     */
    @PostMapping("/test/complete/{paymentId}")
    public ResponseEntity<Map<String, Object>> completeTestPayment(@PathVariable String paymentId) {
        log.info("🧪 ТЕСТ: Запрос на немедленное завершение тестового платежа: {}", paymentId);

        // Проверяем, что тестовый сервис доступен
        if (testPaymentService == null || !testPaymentService.isTestModeEnabled()) {
            return createErrorResponse("Тестовый режим недоступен", HttpStatus.BAD_REQUEST);
        }

        try {
            // Валидация payment ID
            if (paymentId == null || paymentId.trim().isEmpty() || paymentId.length() > 50) {
                return createErrorResponse("Некорректный ID платежа", HttpStatus.BAD_REQUEST);
            }

            boolean success = testPaymentService.completeTestPaymentImmediately(paymentId);

            if (success) {
                log.info("✅ ТЕСТ: Тестовый платеж {} успешно завершен", paymentId);
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Тестовый платеж успешно завершен");
                response.put("payment_id", paymentId);
                return ResponseEntity.ok(response);
            } else {
                log.warn("❌ ТЕСТ: Не удалось завершить тестовый платеж: {}", paymentId);
                return createErrorResponse("Не удалось завершить тестовый платеж", HttpStatus.BAD_REQUEST);
            }

        } catch (Exception e) {
            log.error("💥 ТЕСТ: Ошибка при завершении тестового платежа {}: {}", paymentId, e.getMessage(), e);
            return createErrorResponse("Внутренняя ошибка сервера", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Получение конфигурации тестового режима
     */
    @GetMapping("/test/config")
    public ResponseEntity<Map<String, Object>> getTestConfiguration() {
        if (testPaymentService == null || !testPaymentService.isTestModeEnabled()) {
            return createErrorResponse("Тестовый режим недоступен", HttpStatus.BAD_REQUEST);
        }

        try {
            Map<String, Object> config = testPaymentService.getTestModeConfiguration();
            log.info("🧪 ТЕСТ: Запрос конфигурации тестового режима");

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("config", config);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("💥 ТЕСТ: Ошибка при получении конфигурации: {}", e.getMessage(), e);
            return createErrorResponse("Внутренняя ошибка сервера", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}