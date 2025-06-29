package shit.back.web.controller.admin;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import shit.back.service.admin.shared.AdminAuthenticationService;
import shit.back.service.admin.shared.AdminSecurityHelper;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Базовый контроллер для всех админских контроллеров
 * Содержит общую логику для аутентификации, логирования и обработки ошибок
 * Следует принципам SOLID - Single Responsibility, DRY, KISS
 */
public abstract class AdminBaseController {

    private static final Logger log = LoggerFactory.getLogger(AdminBaseController.class);

    @Autowired
    protected AdminAuthenticationService adminAuthenticationService;

    @Autowired
    protected AdminSecurityHelper adminSecurityHelper;

    /**
     * Создание стандартного ответа об ошибке
     * 
     * @param message сообщение об ошибке
     * @param e       исключение (может быть null)
     * @return Map с информацией об ошибке
     */
    protected Map<String, Object> createErrorResponse(String message, Exception e) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        response.put("message", e != null ? e.getMessage() : "Unknown error");
        response.put("timestamp", LocalDateTime.now());

        if (e != null) {
            log.error("Admin controller error: {}", message, e);
        } else {
            log.warn("Admin controller error: {}", message);
        }

        return response;
    }

    /**
     * Создание успешного ответа
     * 
     * @param message сообщение об успехе
     * @param data    данные (может быть null)
     * @return Map с информацией об успехе
     */
    protected Map<String, Object> createSuccessResponse(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        if (data != null) {
            response.put("data", data);
        }
        response.put("timestamp", LocalDateTime.now());
        return response;
    }

    /**
     * Проверка аутентификации для UI запросов
     * 
     * @param request HTTP запрос
     * @return true если аутентификация прошла успешно
     */
    protected boolean validateAuthentication(HttpServletRequest request) {
        try {
            return adminAuthenticationService.validateAuthentication(request);
        } catch (Exception e) {
            log.error("Authentication validation failed", e);
            return false;
        }
    }

    /**
     * Проверка аутентификации для API запросов
     * 
     * @param request HTTP запрос
     * @return true если аутентификация прошла успешно
     */
    protected boolean validateApiAuthentication(HttpServletRequest request) {
        try {
            return adminAuthenticationService.validateApiRequest(request);
        } catch (Exception e) {
            log.error("API authentication validation failed", e);
            return false;
        }
    }

    /**
     * Логирование админской активности
     * 
     * @param request     HTTP запрос
     * @param action      действие
     * @param description описание
     */
    protected void logAdminActivity(HttpServletRequest request, String action, String description) {
        try {
            adminSecurityHelper.logAdminActivity(request, action, description);
        } catch (Exception e) {
            log.error("Failed to log admin activity: action={}, description={}", action, description, e);
        }
    }

    /**
     * Проверка подозрительной активности
     * 
     * @param request HTTP запрос
     * @return true если обнаружена подозрительная активность
     */
    protected boolean isSuspiciousActivity(HttpServletRequest request) {
        try {
            return adminSecurityHelper.isSuspiciousActivity(request);
        } catch (Exception e) {
            log.error("Failed to check suspicious activity", e);
            return false;
        }
    }

    /**
     * Получение IP адреса клиента
     * 
     * @param request HTTP запрос
     * @return IP адрес клиента
     */
    protected String getClientIpAddress(HttpServletRequest request) {
        try {
            return adminSecurityHelper.getClientIpAddress(request);
        } catch (Exception e) {
            log.error("Failed to get client IP address", e);
            return "unknown";
        }
    }

    /**
     * Создание контекста безопасности
     * 
     * @param request HTTP запрос
     * @return контекст безопасности
     */
    protected Map<String, Object> createSecurityContext(HttpServletRequest request) {
        try {
            return adminSecurityHelper.createSecurityContext(request);
        } catch (Exception e) {
            log.error("Failed to create security context", e);
            return Map.of(
                    "error", "Failed to create security context",
                    "timestamp", LocalDateTime.now());
        }
    }

    /**
     * Обработка неудачной аутентификации для UI
     * 
     * @param request HTTP запрос
     * @return путь для редиректа
     */
    protected String handleAuthenticationFailure(HttpServletRequest request) {
        String clientIp = getClientIpAddress(request);
        log.warn("Unauthorized access attempt from IP: {}", clientIp);
        logAdminActivity(request, "AUTH_FAILURE", "Unauthorized access attempt from " + clientIp);
        return "redirect:/admin/login";
    }
}