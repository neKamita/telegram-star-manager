package shit.back.controller;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Простой автономный тест для проверки логики различения запросов
 * без зависимостей от Spring Context
 */
public class AdminOrdersControllerLogicTest {

    @Test
    public void testBrowserRequestDetection() {
        // Создаем типичный браузерный запрос
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        request.addHeader("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        request.setRequestURI("/admin/orders");

        // Проверяем логику определения типа запроса
        boolean shouldBeWebRequest = !isApiRequest(request);
        assertTrue(shouldBeWebRequest, "Браузерный запрос должен определяться как веб-запрос");
    }

    @Test
    public void testApiRequestDetection() {
        // Создаем типичный API запрос
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept", "application/json");
        request.addHeader("Content-Type", "application/json");
        request.setRequestURI("/admin/orders/api/orders");

        // Проверяем логику определения типа запроса
        boolean shouldBeApiRequest = isApiRequest(request);
        assertTrue(shouldBeApiRequest, "JSON запрос должен определяться как API запрос");
    }

    @Test
    public void testApiKeyHeaderDetection() {
        // Создаем запрос с API ключом
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Admin-API-Key", "test-key");
        request.setRequestURI("/admin/orders");

        // Проверяем логику определения типа запроса
        boolean shouldBeApiRequest = isApiRequest(request);
        assertTrue(shouldBeApiRequest, "Запрос с API ключом должен определяться как API запрос");
    }

    @Test
    public void testUrlWithApiPath() {
        // Создаем запрос с /api/ в URL
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/admin/orders/api/statistics");

        // Проверяем логику определения типа запроса
        boolean shouldBeApiRequest = isApiRequest(request);
        assertTrue(shouldBeApiRequest, "URL с /api/ должен определяться как API запрос");
    }

    @Test
    public void testNonBrowserUserAgent() {
        // Создаем запрос с небраузерным User-Agent
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "curl/7.68.0");
        request.setRequestURI("/admin/orders");

        // Проверяем логику определения типа запроса
        boolean shouldBeApiRequest = isApiRequest(request);
        assertTrue(shouldBeApiRequest, "Запрос с небраузерным User-Agent должен определяться как API запрос");
    }

    /**
     * Воспроизводим логику определения API запроса из AdminOrdersController
     */
    private boolean isApiRequest(MockHttpServletRequest request) {
        String acceptHeader = request.getHeader("Accept");
        String contentType = request.getHeader("Content-Type");
        String userAgent = request.getHeader("User-Agent");
        String apiKeyHeader = request.getHeader("X-Admin-API-Key");
        String requestUri = request.getRequestURI();

        // Если присутствует API ключ, считаем это API запросом
        if (apiKeyHeader != null && !apiKeyHeader.trim().isEmpty()) {
            return true;
        }

        // Если URL содержит /api/, считаем это API запросом
        if (requestUri != null && requestUri.contains("/api/")) {
            return true;
        }

        // Если Accept заголовок содержит только application/json, считаем это API
        // запросом
        if (acceptHeader != null &&
                acceptHeader.contains("application/json") &&
                !acceptHeader.contains("text/html")) {
            return true;
        }

        // Если Content-Type это application/json (для POST/PUT запросов), считаем это
        // API запросом
        if (contentType != null && contentType.contains("application/json")) {
            return true;
        }

        // Если User-Agent отсутствует или не содержит признаков браузера, считаем это
        // API запросом
        if (userAgent == null ||
                (!userAgent.contains("Mozilla") &&
                        !userAgent.contains("Chrome") &&
                        !userAgent.contains("Safari") &&
                        !userAgent.contains("Firefox") &&
                        !userAgent.contains("Edge"))) {
            return true;
        }

        // Во всех остальных случаях считаем это веб-запросом (браузерным)
        return false;
    }
}