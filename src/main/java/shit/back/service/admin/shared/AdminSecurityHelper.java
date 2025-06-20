package shit.back.service.admin.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import shit.back.config.SecurityProperties;
import shit.back.entity.UserActivityLogEntity.ActionType;
import shit.back.service.UserActivityLogService;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;

/**
 * Helper класс для security операций в админской панели
 * Предоставляет утилитарные методы для безопасности
 * Следует принципам SOLID - Single Responsibility Principle
 */
@Component
public class AdminSecurityHelper {

    private static final Logger log = LoggerFactory.getLogger(AdminSecurityHelper.class);

    @Autowired
    private SecurityProperties securityProperties;

    @Autowired
    private UserActivityLogService activityLogService;

    /**
     * Логирование админской активности с контекстом безопасности
     * 
     * @param request HTTP запрос
     * @param action  выполненное действие
     * @param details детали действия
     */
    public void logAdminActivity(HttpServletRequest request, String action, String details) {
        try {
            String remoteAddr = getClientIpAddress(request);
            String userAgent = request.getHeader("User-Agent");
            String requestUri = request.getRequestURI();

            String logMessage = String.format(
                    "[ADMIN] %s | IP: %s | URI: %s | UserAgent: %s | Details: %s",
                    action, remoteAddr, requestUri,
                    truncateUserAgent(userAgent),
                    details != null ? details : "N/A");

            activityLogService.logApplicationActivity(
                    null, "ADMIN", null, null,
                    ActionType.STATE_CHANGED,
                    logMessage);

            log.info("Admin activity logged: {}", action);

        } catch (Exception e) {
            log.error("Failed to log admin activity", e);
        }
    }

    /**
     * Получение реального IP адреса клиента с учетом прокси
     * 
     * @param request HTTP запрос
     * @return IP адрес клиента
     */
    public String getClientIpAddress(HttpServletRequest request) {
        String[] headerNames = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_CLIENT_IP",
                "HTTP_X_FORWARDED_FOR"
        };

        for (String headerName : headerNames) {
            String ip = request.getHeader(headerName);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // Если есть несколько IP через запятую, берем первый
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }

        return request.getRemoteAddr();
    }

    /**
     * Обрезка User-Agent для логирования
     * 
     * @param userAgent исходный User-Agent
     * @return обрезанный User-Agent
     */
    private String truncateUserAgent(String userAgent) {
        if (userAgent == null) {
            return "Unknown";
        }
        return userAgent.length() > 100 ? userAgent.substring(0, 100) + "..." : userAgent;
    }

    /**
     * Создание контекста безопасности для запроса
     * 
     * @param request HTTP запрос
     * @return Map с контекстом безопасности
     */
    public Map<String, Object> createSecurityContext(HttpServletRequest request) {
        Map<String, Object> context = new HashMap<>();

        try {
            context.put("clientIp", getClientIpAddress(request));
            context.put("userAgent", request.getHeader("User-Agent"));
            context.put("requestUri", request.getRequestURI());
            context.put("requestMethod", request.getMethod());
            context.put("timestamp", LocalDateTime.now());
            context.put("sessionId", request.getSession(false) != null ? request.getSession().getId() : "none");

            // Информация о заголовках
            Map<String, String> securityHeaders = new HashMap<>();
            securityHeaders.put("Accept", request.getHeader("Accept"));
            securityHeaders.put("Content-Type", request.getHeader("Content-Type"));
            securityHeaders.put("Authorization", request.getHeader("Authorization") != null ? "present" : "none");
            securityHeaders.put("X-Admin-API-Key", request.getHeader("X-Admin-API-Key") != null ? "present" : "none");

            context.put("headers", securityHeaders);

        } catch (Exception e) {
            log.warn("Error creating security context", e);
            context.put("error", "Failed to create full context");
        }

        return context;
    }

    /**
     * Проверка подозрительной активности
     * 
     * @param request HTTP запрос
     * @return true если активность подозрительна
     */
    public boolean isSuspiciousActivity(HttpServletRequest request) {
        try {
            String userAgent = request.getHeader("User-Agent");
            String referer = request.getHeader("Referer");
            String requestUri = request.getRequestURI();

            // Проверка на ботов или сканеры
            if (userAgent != null) {
                String lowerUA = userAgent.toLowerCase();
                if (lowerUA.contains("bot") || lowerUA.contains("crawler") ||
                        lowerUA.contains("spider") || lowerUA.contains("scanner")) {
                    log.warn("Bot/Scanner detected: {}", userAgent);
                    return true;
                }
            }

            // Проверка на подозрительные URI паттерны
            if (requestUri != null) {
                String lowerUri = requestUri.toLowerCase();
                if (lowerUri.contains("..") || lowerUri.contains("script") ||
                        lowerUri.contains("eval") || lowerUri.contains("exec")) {
                    log.warn("Suspicious URI pattern: {}", requestUri);
                    return true;
                }
            }

            // Проверка отсутствия Referer для критических операций
            if (request.getMethod().equals("POST") &&
                    (referer == null || referer.trim().isEmpty())) {
                log.debug("POST request without Referer from IP: {}", getClientIpAddress(request));
                // Это может быть подозрительно, но не всегда
                return false;
            }

        } catch (Exception e) {
            log.warn("Error checking suspicious activity", e);
        }

        return false;
    }

    /**
     * Sanitizация входных данных для безопасности
     * 
     * @param input входная строка
     * @return очищенная строка
     */
    public String sanitizeInput(String input) {
        if (input == null) {
            return null;
        }

        return input.trim()
                .replaceAll("<", "&lt;")
                .replaceAll(">", "&gt;")
                .replaceAll("\"", "&quot;")
                .replaceAll("'", "&#x27;")
                .replaceAll("&", "&amp;");
    }

    /**
     * Проверка доступности админских функций
     * 
     * @return true если админские функции доступны
     */
    public boolean isAdminFunctionsEnabled() {
        try {
            // Можно добавить проверку конфигурации
            return securityProperties != null;
        } catch (Exception e) {
            log.error("Error checking admin functions availability", e);
            return false;
        }
    }

    /**
     * Создание безопасного лога для отладки
     * 
     * @param action действие
     * @param data   данные
     * @return строка для логирования
     */
    public String createSecureLogMessage(String action, Object data) {
        StringBuilder logMessage = new StringBuilder();
        logMessage.append("[ADMIN_SECURE] ").append(action);

        if (data != null) {
            String dataStr = data.toString();
            // Убираем потенциально чувствительные данные
            dataStr = dataStr.replaceAll("(?i)(password|token|key|secret)=[^\\s,}]+", "$1=***");
            logMessage.append(" | Data: ").append(dataStr);
        }

        logMessage.append(" | Timestamp: ").append(LocalDateTime.now());

        return logMessage.toString();
    }

    /**
     * Генерация уникального идентификатора для сессии
     * 
     * @return уникальный идентификатор
     */
    public String generateSessionId() {
        return "admin_" + System.currentTimeMillis() + "_" +
                java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Проверка валидности временного токена (если используется)
     * 
     * @param token токен
     * @return true если токен валиден
     */
    public boolean isValidTemporaryToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }

        try {
            // Простая проверка формата временного токена
            // В реальной системе здесь была бы проверка подписи и времени жизни
            return token.matches("^admin_\\d+_[a-f0-9]{8}$");
        } catch (Exception e) {
            log.warn("Error validating temporary token", e);
            return false;
        }
    }
}