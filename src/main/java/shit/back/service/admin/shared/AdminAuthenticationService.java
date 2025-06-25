package shit.back.service.admin.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import shit.back.config.SecurityProperties;
import shit.back.security.RateLimitService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Унифицированный сервис аутентификации для админской панели
 * Выносит общую логику аутентификации из контроллеров
 * Следует принципам SOLID - Single Responsibility Principle
 */
@Service
public class AdminAuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthenticationService.class);

    /**
     * Внутренние админские endpoints, которые не требуют API ключ
     * Это AJAX запросы из админской панели для внутреннего использования
     */
    private static final String[] ADMIN_INTERNAL_ENDPOINTS = {
            "/admin/api/dashboard-data",
            "/admin/api/system-health",
            "/admin/api/dashboard/overview",
            "/admin/api/dashboard/full",
            "/admin/api/dashboard/system-health",
            "/admin/api/dashboard/recent-activity",
            "/admin/api/dashboard/quick-stats",
            "/admin/api/dashboard/activity-statistics",
            "/admin/api/dashboard/refresh-cache",
            "/admin/api/dashboard/activity-stream",
            // ИСПРАВЛЕНИЕ SSE: Добавляем SSE endpoints для мониторинга
            "/admin/api/metrics/stream",
            "/admin/api/metrics/current",
            "/admin/api/metrics/health",
            "/admin/api/metrics/stats",
            "/admin/api/metrics/test-connection",
            "/admin/api/monitoring-fast",
            "/admin/api/environment-info"
    };

    @Autowired
    private SecurityProperties securityProperties;

    @Autowired
    private RateLimitService rateLimitService;

    /**
     * Универсальная проверка API запроса с аутентификацией и rate limiting
     *
     * @param request HTTP запрос
     * @return true если запрос валиден
     */
    public boolean validateApiRequest(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        String requestUri = request.getRequestURI();

        try {
            // Проверяем, является ли это внутренним админским endpoint
            if (isAdminInternalEndpoint(requestUri)) {
                log.debug("Admin internal endpoint allowed without API key from IP: {} for URI: {}", remoteAddr,
                        requestUri);
                return true;
            }

            // Проверка rate limiting для внешних API запросов
            RateLimitService.RateLimitResult rateLimitResult = rateLimitService.checkApiLimit(remoteAddr);
            if (!rateLimitResult.isAllowed()) {
                log.warn("Rate limit exceeded for admin API from IP: {}", remoteAddr);
                return false;
            }

            // Определяем тип запроса и проводим соответствующую аутентификацию
            if (isApiRequest(request)) {
                return validateApiKey(request, remoteAddr);
            } else {
                // Для веб-запросов (браузерных) разрешаем доступ без API ключа
                log.debug("Web request (browser) allowed from IP: {}", remoteAddr);
                return true;
            }

        } catch (Exception e) {
            log.error("Error during admin authentication from IP: {}", remoteAddr, e);
            return false;
        }
    }

    /**
     * Проверка только аутентификации без rate limiting
     *
     * @param request HTTP запрос
     * @return true если аутентификация прошла успешно
     */
    public boolean validateAuthentication(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        String requestUri = request.getRequestURI();

        try {
            // Проверяем, является ли это внутренним админским endpoint
            if (isAdminInternalEndpoint(requestUri)) {
                log.debug("Admin internal endpoint allowed without API key from IP: {} for URI: {}", remoteAddr,
                        requestUri);
                return true;
            }

            if (isApiRequest(request)) {
                return validateApiKey(request, remoteAddr);
            } else {
                log.debug("Web request (browser) allowed from IP: {}", remoteAddr);
                return true;
            }

        } catch (Exception e) {
            log.error("Error during admin authentication from IP: {}", remoteAddr, e);
            return false;
        }
    }

    /**
     * Определяет, является ли запрос API-запросом на основе заголовков
     * 
     * @param request HTTP запрос
     * @return true если это API запрос
     */
    private boolean isApiRequest(HttpServletRequest request) {
        String acceptHeader = request.getHeader("Accept");
        String contentType = request.getHeader("Content-Type");
        String userAgent = request.getHeader("User-Agent");
        String apiKeyHeader = request.getHeader(securityProperties.getApi().getHeaderName());
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

    /**
     * Валидация API ключа для API запросов
     * 
     * @param request    HTTP запрос
     * @param remoteAddr IP адрес
     * @return true если API ключ валиден
     */
    private boolean validateApiKey(HttpServletRequest request, String remoteAddr) {
        // Получаем API ключ из конфигурации
        String expectedApiKey = securityProperties.getApi().getKey();

        // Проверяем, что API ключ настроен
        if (expectedApiKey == null || expectedApiKey.trim().isEmpty()) {
            log.error("Admin API key is not configured. Set API_SECRET_KEY environment variable");
            return false;
        }

        // Получаем API ключ из заголовка запроса
        String providedApiKey = request.getHeader(securityProperties.getApi().getHeaderName());

        // Проверяем наличие ключа в API запросе
        if (providedApiKey == null || providedApiKey.trim().isEmpty()) {
            log.warn("API request without API key from IP: {}", remoteAddr);
            return false;
        }

        // Используем constant-time сравнение для предотвращения timing attacks
        boolean isValid = constantTimeEquals(expectedApiKey, providedApiKey);

        if (isValid) {
            log.debug("Valid admin API key provided from IP: {}", remoteAddr);
            return true;
        } else {
            log.warn("Invalid admin API key provided from IP: {}", remoteAddr);
            return false;
        }
    }

    /**
     * Constant-time сравнение строк для предотвращения timing attacks
     * 
     * @param expected ожидаемая строка
     * @param provided предоставленная строка
     * @return true если строки равны
     */
    private boolean constantTimeEquals(String expected, String provided) {
        if (expected == null || provided == null) {
            return false;
        }

        byte[] expectedBytes = expected.getBytes();
        byte[] providedBytes = provided.getBytes();

        // Если длины не совпадают, всё равно выполняем полное сравнение
        // для поддержания constant-time
        int expectedLength = expectedBytes.length;
        int providedLength = providedBytes.length;
        int maxLength = Math.max(expectedLength, providedLength);

        int result = expectedLength ^ providedLength;

        for (int i = 0; i < maxLength; i++) {
            byte expectedByte = (i < expectedLength) ? expectedBytes[i] : 0;
            byte providedByte = (i < providedLength) ? providedBytes[i] : 0;
            result |= expectedByte ^ providedByte;
        }

        return result == 0;
    }

    /**
     * Проверяет, является ли endpoint внутренним админским
     *
     * @param requestUri URI запроса
     * @return true если это внутренний админский endpoint
     */
    private boolean isAdminInternalEndpoint(String requestUri) {
        if (requestUri == null) {
            return false;
        }

        for (String endpoint : ADMIN_INTERNAL_ENDPOINTS) {
            if (requestUri.equals(endpoint) || requestUri.startsWith(endpoint + "/")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Получение информации о типе запроса для логирования
     *
     * @param request HTTP запрос
     * @return строковое описание типа запроса
     */
    public String getRequestTypeInfo(HttpServletRequest request) {
        if (isApiRequest(request)) {
            return "API Request";
        } else {
            return "Web Request (Browser)";
        }
    }
}