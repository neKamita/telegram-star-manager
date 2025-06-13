package shit.back.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import shit.back.config.PaymentConfigurationProperties;
import shit.back.service.PaymentService;
import shit.back.security.SecurityValidator;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PaymentCallbackControllerSecurityTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentConfigurationProperties paymentConfig;

    @Mock
    private SecurityValidator securityValidator;

    @InjectMocks
    private PaymentCallbackController controller;

    private static final String TEST_SECRET = "test_secret_key_for_hmac_verification_32chars";

    @BeforeEach
    void setUp() {
        // Устанавливаем тестовый секрет
        ReflectionTestUtils.setField(controller, "tonWebhookSecret", TEST_SECRET);
    }

    @Test
    void testVerifyTonSignature_ValidSignature_ShouldReturnTrue() throws Exception {
        // Подготавливаем тестовые данные
        Map<String, String> params = new HashMap<>();
        params.put("payment_id", "test123");
        params.put("status", "completed");
        params.put("amount", "100");

        // Вычисляем корректную подпись
        String signatureString = "amount=100&payment_id=test123&status=completed";
        String expectedSignature = computeHmacSha256(signatureString, TEST_SECRET);

        Map<String, String> headers = new HashMap<>();
        headers.put("x-signature", expectedSignature);

        // Используем рефлексию для вызова приватного метода
        Method verifyMethod = PaymentCallbackController.class.getDeclaredMethod(
                "verifyTonSignature", Map.class, Map.class);
        verifyMethod.setAccessible(true);

        // Выполняем тест
        boolean result = (boolean) verifyMethod.invoke(controller, params, headers);

        assertTrue(result, "Верификация корректной подписи должна возвращать true");
    }

    @Test
    void testVerifyTonSignature_InvalidSignature_ShouldReturnFalse() throws Exception {
        // Подготавливаем тестовые данные
        Map<String, String> params = new HashMap<>();
        params.put("payment_id", "test123");
        params.put("status", "completed");
        params.put("amount", "100");

        Map<String, String> headers = new HashMap<>();
        headers.put("x-signature", "invalid_signature");

        // Используем рефлексию для вызова приватного метода
        Method verifyMethod = PaymentCallbackController.class.getDeclaredMethod(
                "verifyTonSignature", Map.class, Map.class);
        verifyMethod.setAccessible(true);

        // Выполняем тест
        boolean result = (boolean) verifyMethod.invoke(controller, params, headers);

        assertFalse(result, "Верификация некорректной подписи должна возвращать false");
    }

    @Test
    void testVerifyTonSignature_MissingSignature_ShouldReturnFalse() throws Exception {
        // Подготавливаем тестовые данные без подписи
        Map<String, String> params = new HashMap<>();
        params.put("payment_id", "test123");
        params.put("status", "completed");

        Map<String, String> headers = new HashMap<>();

        // Используем рефлексию для вызова приватного метода
        Method verifyMethod = PaymentCallbackController.class.getDeclaredMethod(
                "verifyTonSignature", Map.class, Map.class);
        verifyMethod.setAccessible(true);

        // Выполняем тест
        boolean result = (boolean) verifyMethod.invoke(controller, params, headers);

        assertFalse(result, "Отсутствие подписи должно возвращать false");
    }

    @Test
    void testVerifyTonSignature_EmptySecret_ShouldReturnFalse() throws Exception {
        // Устанавливаем пустой секрет
        ReflectionTestUtils.setField(controller, "tonWebhookSecret", "");

        Map<String, String> params = new HashMap<>();
        params.put("payment_id", "test123");

        Map<String, String> headers = new HashMap<>();
        headers.put("x-signature", "any_signature");

        // Используем рефлексию для вызова приватного метода
        Method verifyMethod = PaymentCallbackController.class.getDeclaredMethod(
                "verifyTonSignature", Map.class, Map.class);
        verifyMethod.setAccessible(true);

        // Выполняем тест
        boolean result = (boolean) verifyMethod.invoke(controller, params, headers);

        assertFalse(result, "Пустой секрет должен возвращать false");
    }

    @Test
    void testConstantTimeEquals_SameStrings_ShouldReturnTrue() throws Exception {
        String str1 = "test_string";
        String str2 = "test_string";

        Method constantTimeEqualsMethod = PaymentCallbackController.class.getDeclaredMethod(
                "constantTimeEquals", String.class, String.class);
        constantTimeEqualsMethod.setAccessible(true);

        boolean result = (boolean) constantTimeEqualsMethod.invoke(controller, str1, str2);

        assertTrue(result, "Идентичные строки должны возвращать true");
    }

    @Test
    void testConstantTimeEquals_DifferentStrings_ShouldReturnFalse() throws Exception {
        String str1 = "test_string";
        String str2 = "different_string";

        Method constantTimeEqualsMethod = PaymentCallbackController.class.getDeclaredMethod(
                "constantTimeEquals", String.class, String.class);
        constantTimeEqualsMethod.setAccessible(true);

        boolean result = (boolean) constantTimeEqualsMethod.invoke(controller, str1, str2);

        assertFalse(result, "Разные строки должны возвращать false");
    }

    @Test
    void testBuildTonSignatureString_ShouldSortParametersCorrectly() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("payment_id", "test123");
        params.put("amount", "100");
        params.put("status", "completed");

        Method buildSignatureStringMethod = PaymentCallbackController.class.getDeclaredMethod(
                "buildTonSignatureString", Map.class);
        buildSignatureStringMethod.setAccessible(true);

        String result = (String) buildSignatureStringMethod.invoke(controller, params);

        assertEquals("amount=100&payment_id=test123&status=completed", result,
                "Параметры должны быть отсортированы по ключу");
    }

    /**
     * Вспомогательный метод для вычисления HMAC-SHA256
     */
    private String computeHmacSha256(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);

        byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

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
}