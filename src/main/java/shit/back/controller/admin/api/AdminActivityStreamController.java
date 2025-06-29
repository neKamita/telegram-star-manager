package shit.back.controller.admin.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import shit.back.service.UserActivityLogService;
import shit.back.service.activity.UserActivityStatisticsService;
import shit.back.service.admin.shared.AdminAuthenticationService;
import shit.back.service.admin.shared.AdminSecurityHelper;
import shit.back.entity.UserActivityLogEntity;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;

/**
 * ИСПРАВЛЕНИЕ: Отдельный контроллер для SSE activity streams
 * Решает проблему 404 ошибки для /admin/api/activity-stream-categorized
 */
@RestController
@RequestMapping("/admin/api")
public class AdminActivityStreamController {

    private static final Logger log = LoggerFactory.getLogger(AdminActivityStreamController.class);

    @Autowired
    private UserActivityLogService userActivityLogService;

    @Autowired
    private AdminAuthenticationService adminAuthenticationService;

    @Autowired
    private AdminSecurityHelper adminSecurityHelper;

    /**
     * ИСПРАВЛЕНИЕ: Server-Sent Events для activity stream с категориями
     * Правильный URL mapping без /dashboard префикса
     */
    @GetMapping(value = "/activity-stream-categorized", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter getActivityStreamCategorized(
            @RequestParam(value = "category", required = false) String categoryParam,
            HttpServletRequest request) {

        long startTime = System.currentTimeMillis();

        log.info("🔧 DEBUG: SSE connection request received - URL: {}, category: {}, timestamp: {}",
                request.getRequestURL(), categoryParam, System.currentTimeMillis());

        try {
            // Аутентификация
            if (!adminAuthenticationService.validateApiRequest(request)) {
                log.warn("🔧 DEBUG: SSE authentication failed for client: {}", request.getRemoteAddr());
                SseEmitter emitter = new SseEmitter(0L);
                try {
                    emitter.completeWithError(new RuntimeException("Unauthorized"));
                } catch (Exception ex) {
                    log.error("Error completing SSE with unauthorized error", ex);
                }
                return emitter;
            }

            String clientId = "admin-categorized-" + UUID.randomUUID().toString().substring(0, 8);
            log.info("🔧 DEBUG: Creating categorized SSE connection: {} with category: {}", clientId, categoryParam);

            // Парсим категорию
            UserActivityLogEntity.LogCategory category = null;
            if (categoryParam != null && !"ALL".equals(categoryParam)) {
                try {
                    category = UserActivityLogEntity.LogCategory.valueOf(categoryParam);
                    log.debug("🔧 DEBUG: Parsed category: {}", category);
                } catch (IllegalArgumentException e) {
                    log.warn("🔧 DEBUG: Invalid category parameter: {}, using ALL", categoryParam);
                }
            }

            // Логирование подключения
            adminSecurityHelper.logAdminActivity(request, "API_SSE_CATEGORIZED_CONNECT",
                    "Подключение к категоризированному потоку активности: " + clientId + ", категория: "
                            + categoryParam);

            SseEmitter emitter = userActivityLogService.createSseConnection(clientId, category);

            long setupTime = System.currentTimeMillis() - startTime;
            log.info("🔧 DEBUG: SSE connection setup completed in {}ms for client: {}", setupTime, clientId);

            return emitter;

        } catch (Exception e) {
            log.error("🔧 DEBUG: Error creating categorized activity stream SSE connection", e);
            SseEmitter emitter = new SseEmitter(0L);
            try {
                emitter.completeWithError(e);
            } catch (Exception ex) {
                log.error("Error completing categorized SSE with error", ex);
            }
            return emitter;
        }
    }

    /**
     * ИСПРАВЛЕНИЕ: Базовый SSE endpoint без категорий
     */
    @GetMapping(value = "/activity-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter getActivityStream(HttpServletRequest request) {

        log.info("🔧 DEBUG: Basic SSE connection request received - URL: {}", request.getRequestURL());

        try {
            // Аутентификация
            if (!adminAuthenticationService.validateApiRequest(request)) {
                SseEmitter emitter = new SseEmitter(0L);
                try {
                    emitter.completeWithError(new RuntimeException("Unauthorized"));
                } catch (Exception ex) {
                    log.error("Error completing SSE with unauthorized error", ex);
                }
                return emitter;
            }

            String clientId = "admin-basic-" + UUID.randomUUID().toString().substring(0, 8);
            log.info("🔧 DEBUG: Creating basic SSE connection: {}", clientId);

            // Логирование подключения
            adminSecurityHelper.logAdminActivity(request, "API_SSE_CONNECT",
                    "Подключение к потоку активности через SSE: " + clientId);

            return userActivityLogService.createSseConnection(clientId);

        } catch (Exception e) {
            log.error("🔧 DEBUG: Error creating activity stream SSE connection", e);
            SseEmitter emitter = new SseEmitter(0L);
            try {
                emitter.completeWithError(e);
            } catch (Exception ex) {
                log.error("Error completing SSE with error", ex);
            }
            return emitter;
        }
    }

    /**
     * ИСПРАВЛЕНИЕ: Получение статистики по категориям с поддержкой фильтрации
     */
    @GetMapping(value = "/category-statistics", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getCategoryStatistics(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(value = "category", required = false) String categoryParam,
            HttpServletRequest request) {
        try {
            log.info("🔧 DEBUG: Category statistics request - hours: {}, category: {}", hours, categoryParam);

            // Аутентификация
            if (!adminAuthenticationService.validateApiRequest(request)) {
                log.warn("🔧 DEBUG: Authentication failed for category statistics");
                return ResponseEntity.status(401)
                        .body(Map.of("error", "Unauthorized access"));
            }

            // ИСПРАВЛЕНИЕ: Получаем полную статистику для правильного отображения счетчиков
            UserActivityStatisticsService.CategoryStatistics stats = userActivityLogService
                    .getCategoryStatistics(hours);

            if (stats != null) {
                // ИСПРАВЛЕНИЕ: Создаем адаптированный ответ с учетом текущей категории
                Map<String, Object> response = new java.util.HashMap<>();

                // Всегда возвращаем полную статистику для корректного отображения всех
                // счетчиков
                response.put("totalLogs", stats.getTelegramBotActivities() + stats.getApplicationActivities()
                        + stats.getSystemActivities());
                response.put("telegramBotLogs", stats.getTelegramBotActivities());
                response.put("applicationLogs", stats.getApplicationActivities());
                response.put("systemLogs", stats.getSystemActivities());
                response.put("keyLogs", stats.getTelegramBotKeyActivities() + stats.getApplicationKeyActivities()
                        + stats.getSystemKeyActivities());
                response.put("periodHours", hours);
                response.put("currentCategory", categoryParam != null ? categoryParam : "ALL");

                // ДИАГНОСТИКА: Логируем детали статистики
                log.debug(
                        "🔧 COUNTER_DEBUG: Category statistics response - category: {}, total: {}, telegram: {}, app: {}, system: {}, key: {}",
                        categoryParam, response.get("totalLogs"), response.get("telegramBotLogs"),
                        response.get("applicationLogs"), response.get("systemLogs"), response.get("keyLogs"));

                adminSecurityHelper.logAdminActivity(request, "API_CATEGORY_STATS",
                        "Получение статистики категорий за " + hours + " часов для категории: " + categoryParam);

                return ResponseEntity.ok(response);
            } else {
                // ИСПРАВЛЕНИЕ: Возвращаем нулевую статистику в правильном формате
                Map<String, Object> response = Map.of(
                        "totalLogs", 0,
                        "telegramBotLogs", 0,
                        "applicationLogs", 0,
                        "systemLogs", 0,
                        "keyLogs", 0,
                        "periodHours", hours,
                        "currentCategory", categoryParam != null ? categoryParam : "ALL");

                return ResponseEntity.ok(response);
            }

        } catch (Exception e) {
            log.error("🔧 DEBUG: Error getting category statistics", e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to get category statistics", "message", e.getMessage()));
        }
    }

    /**
     * ИСПРАВЛЕНИЕ: Получение логов активности по категориям
     */
    @GetMapping(value = "/activity-logs-by-category", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getActivityLogsByCategory(
            @RequestParam(value = "category", required = false) String categoryParam,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        try {
            log.info("🔧 DEBUG: Activity logs by category request - category: {}, page: {}, size: {}",
                    categoryParam, page, size);

            // Аутентификация
            if (!adminAuthenticationService.validateApiRequest(request)) {
                log.warn("🔧 DEBUG: Authentication failed for activity logs by category");
                return ResponseEntity.status(401)
                        .body(Map.of("error", "Unauthorized access"));
            }

            Pageable pageable = PageRequest.of(page, size);
            Page<UserActivityLogEntity> activities;

            if (categoryParam != null && !"ALL".equals(categoryParam)) {
                try {
                    UserActivityLogEntity.LogCategory category = UserActivityLogEntity.LogCategory
                            .valueOf(categoryParam);
                    activities = userActivityLogService.getActivitiesByCategory(category, pageable);
                    log.debug("🔧 DEBUG: Retrieved {} activities for category {}", activities.getSize(), category);
                } catch (IllegalArgumentException e) {
                    log.warn("🔧 DEBUG: Invalid category: {}, returning all activities", categoryParam);
                    activities = userActivityLogService.getAllActivities(pageable);
                }
            } else {
                activities = userActivityLogService.getAllActivities(pageable);
                log.debug("🔧 DEBUG: Retrieved {} activities for ALL categories", activities.getSize());
            }

            adminSecurityHelper.logAdminActivity(request, "API_ACTIVITY_LOGS_BY_CATEGORY",
                    "Получение логов активности по категории: " + categoryParam);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "activities", activities.getContent(),
                    "totalElements", activities.getTotalElements(),
                    "totalPages", activities.getTotalPages(),
                    "size", activities.getSize(),
                    "number", activities.getNumber(),
                    "category", categoryParam != null ? categoryParam : "ALL"));

        } catch (Exception e) {
            log.error("🔧 DEBUG: Error getting activity logs by category", e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to get activity logs", "message", e.getMessage()));
        }
    }
}