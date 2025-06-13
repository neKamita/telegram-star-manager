package shit.back.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import shit.back.config.SecurityProperties;
import shit.back.security.RateLimitService;
import shit.back.security.SecurityValidator;
import shit.back.service.OrderService;
import shit.back.service.UserActivityLogService;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Тест для проверки логики аутентификации в AdminOrdersController
 * Проверяет различение веб и API запросов
 */
@ExtendWith(MockitoExtension.class)
public class AdminOrdersControllerAuthTest {

    @Mock
    private OrderService orderService;

    @Mock
    private UserActivityLogService activityLogService;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private SecurityValidator securityValidator;

    @Mock
    private SecurityProperties securityProperties;

    @Mock
    private SecurityProperties.Api apiProperties;

    @InjectMocks
    private AdminOrdersController adminOrdersController;

    @Test
    public void testWebRequestShouldBeAllowed() throws Exception {
        // Подготовка мока для SecurityProperties
        when(securityProperties.getApi()).thenReturn(apiProperties);
        when(apiProperties.getKey()).thenReturn("test-api-key");

        // Создаем веб-запрос (браузерный)
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        request.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        request.setRequestURI("/admin/orders");

        // Используем рефлексию для доступа к private методу
        Method isValidAdminRequestMethod = AdminOrdersController.class.getDeclaredMethod("isValidAdminRequest",
                jakarta.servlet.http.HttpServletRequest.class);
        isValidAdminRequestMethod.setAccessible(true);

        // Проверяем, что веб-запрос разрешен без API ключа
        boolean result = (Boolean) isValidAdminRequestMethod.invoke(adminOrdersController, request);
        assertTrue(result, "Веб-запрос из браузера должен быть разрешен без API ключа");
    }

    @Test
    public void testApiRequestWithValidKeyShoudBeAllowed() throws Exception {
        // Подготовка мока для SecurityProperties
        when(securityProperties.getApi()).thenReturn(apiProperties);
        when(apiProperties.getKey()).thenReturn("test-api-key");

        // Создаем API запрос с валидным ключом
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("Accept", "application/json");
        request.addHeader("X-Admin-API-Key", "test-api-key");
        request.setRequestURI("/admin/orders/api/orders");

        // Используем рефлексию для доступа к private методу
        Method isValidAdminRequestMethod = AdminOrdersController.class.getDeclaredMethod("isValidAdminRequest",
                jakarta.servlet.http.HttpServletRequest.class);
        isValidAdminRequestMethod.setAccessible(true);

        // Проверяем, что API запрос с валидным ключом разрешен
        boolean result = (Boolean) isValidAdminRequestMethod.invoke(adminOrdersController, request);
        assertTrue(result, "API запрос с валидным ключом должен быть разрешен");
    }

    @Test
    public void testApiRequestWithoutKeyShouldBeDenied() throws Exception {
        // Подготовка мока для SecurityProperties
        when(securityProperties.getApi()).thenReturn(apiProperties);
        when(apiProperties.getKey()).thenReturn("test-api-key");

        // Создаем API запрос без ключа
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("Accept", "application/json");
        request.setRequestURI("/admin/orders/api/orders");

        // Используем рефлексию для доступа к private методу
        Method isValidAdminRequestMethod = AdminOrdersController.class.getDeclaredMethod("isValidAdminRequest",
                jakarta.servlet.http.HttpServletRequest.class);
        isValidAdminRequestMethod.setAccessible(true);

        // Проверяем, что API запрос без ключа отклонен
        boolean result = (Boolean) isValidAdminRequestMethod.invoke(adminOrdersController, request);
        assertFalse(result, "API запрос без ключа должен быть отклонен");
    }

    @Test
    public void testApiRequestWithInvalidKeyShouldBeDenied() throws Exception {
        // Подготовка мока для SecurityProperties
        when(securityProperties.getApi()).thenReturn(apiProperties);
        when(apiProperties.getKey()).thenReturn("test-api-key");

        // Создаем API запрос с неверным ключом
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("Accept", "application/json");
        request.addHeader("X-Admin-API-Key", "wrong-api-key");
        request.setRequestURI("/admin/orders/api/orders");

        // Используем рефлексию для доступа к private методу
        Method isValidAdminRequestMethod = AdminOrdersController.class.getDeclaredMethod("isValidAdminRequest",
                jakarta.servlet.http.HttpServletRequest.class);
        isValidAdminRequestMethod.setAccessible(true);

        // Проверяем, что API запрос с неверным ключом отклонен
        boolean result = (Boolean) isValidAdminRequestMethod.invoke(adminOrdersController, request);
        assertFalse(result, "API запрос с неверным ключом должен быть отклонен");
    }

    @Test
    public void testIsApiRequestDetection() throws Exception {
        // Используем рефлексию для доступа к private методу
        Method isApiRequestMethod = AdminOrdersController.class.getDeclaredMethod("isApiRequest",
                jakarta.servlet.http.HttpServletRequest.class);
        isApiRequestMethod.setAccessible(true);

        // Тест 1: Браузерный запрос
        MockHttpServletRequest webRequest = new MockHttpServletRequest();
        webRequest.addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        webRequest.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        webRequest.setRequestURI("/admin/orders");

        boolean isApiRequest1 = (Boolean) isApiRequestMethod.invoke(adminOrdersController, webRequest);
        assertFalse(isApiRequest1, "Браузерный запрос не должен определяться как API запрос");

        // Тест 2: API запрос с JSON Accept
        MockHttpServletRequest apiRequest = new MockHttpServletRequest();
        apiRequest.addHeader("Accept", "application/json");
        apiRequest.setRequestURI("/admin/orders/data");

        boolean isApiRequest2 = (Boolean) isApiRequestMethod.invoke(adminOrdersController, apiRequest);
        assertTrue(isApiRequest2, "Запрос с Accept: application/json должен определяться как API запрос");

        // Тест 3: Запрос с API ключом
        MockHttpServletRequest apiKeyRequest = new MockHttpServletRequest();
        apiKeyRequest.addHeader("X-Admin-API-Key", "some-key");
        apiKeyRequest.setRequestURI("/admin/orders");

        boolean isApiRequest3 = (Boolean) isApiRequestMethod.invoke(adminOrdersController, apiKeyRequest);
        assertTrue(isApiRequest3, "Запрос с API ключом должен определяться как API запрос");

        // Тест 4: URL содержит /api/
        MockHttpServletRequest apiUrlRequest = new MockHttpServletRequest();
        apiUrlRequest.setRequestURI("/admin/orders/api/orders");

        boolean isApiRequest4 = (Boolean) isApiRequestMethod.invoke(adminOrdersController, apiUrlRequest);
        assertTrue(isApiRequest4, "Запрос с /api/ в URL должен определяться как API запрос");
    }
}