package shit.back.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import shit.back.entity.UserActivityLogEntity.ActionType;
import shit.back.entity.UserActivityLogEntity.LogCategory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Сервис для захвата важных системных логов и их сохранения в activity logs
 * Интегрируется с существующей системой UserActivityLogService
 */
@Service
public class SystemLogCaptureService {

    private static final Logger log = LoggerFactory.getLogger(SystemLogCaptureService.class);

    @Autowired
    private UserActivityLogService userActivityLogService;

    // Паттерны для извлечения полезной информации из логов
    private static final Pattern SQL_PATTERN = Pattern
            .compile("(?i)(select|insert|update|delete).*?(?:from|into|set)\\s+(\\w+)", Pattern.DOTALL);
    private static final Pattern URL_PATTERN = Pattern.compile("(GET|POST|PUT|DELETE)\\s+([^\\s]+)");
    private static final Pattern USER_ID_PATTERN = Pattern.compile("(?i)user[\\s_-]?id[\\s=:]+([0-9]+)");
    private static final Pattern ORDER_ID_PATTERN = Pattern.compile("(?i)order[\\s_-]?id[\\s=:]+([A-Z0-9]{8})");
    private static final Pattern ERROR_CODE_PATTERN = Pattern.compile("(?i)error[\\s_-]?code[\\s=:]+([0-9]+)");

    // Системный пользователь для логов
    private static final Long SYSTEM_USER_ID = 0L;
    private static final String SYSTEM_USERNAME = "SYSTEM";

    /**
     * Обработка логов Spring DispatcherServlet
     */
    @Async
    public void captureDispatcherServletLog(String level, String message, String loggerName) {
        try {
            if (!shouldCaptureDispatcherLog(message)) {
                return;
            }

            ActionType actionType = determineActionTypeByLevel(level);
            String description = formatDispatcherLogDescription(message);
            String details = buildLogDetails(level, loggerName, message);

            userActivityLogService.logSystemActivityWithDetails(description, actionType, details);

            log.debug("Captured DispatcherServlet log: {}", description);

        } catch (Exception e) {
            log.error("Error capturing DispatcherServlet log: {}", e.getMessage(), e);
        }
    }

    /**
     * Обработка логов BackgroundMetricsService
     */
    @Async
    public void captureBackgroundMetricsLog(String level, String message, String loggerName) {
        try {
            if (!shouldCaptureBackgroundLog(message)) {
                return;
            }

            ActionType actionType = level.equals("ERROR") ? ActionType.SYSTEM_ERROR : ActionType.BACKGROUND_TASK;
            String description = formatBackgroundLogDescription(message);
            String details = buildLogDetails(level, loggerName, message);

            userActivityLogService.logSystemActivityWithDetails(description, actionType, details);

            log.debug("Captured BackgroundMetrics log: {}", description);

        } catch (Exception e) {
            log.error("Error capturing BackgroundMetrics log: {}", e.getMessage(), e);
        }
    }

    /**
     * Обработка логов AdminDashboardService
     */
    @Async
    public void captureAdminDashboardLog(String level, String message, String loggerName) {
        try {
            if (!shouldCaptureAdminLog(message)) {
                return;
            }

            ActionType actionType = determineActionTypeByLevel(level);
            String description = formatAdminLogDescription(message);
            String details = buildLogDetails(level, loggerName, message);

            userActivityLogService.logSystemActivityWithDetails(description, actionType, details);

            log.debug("Captured AdminDashboard log: {}", description);

        } catch (Exception e) {
            log.error("Error capturing AdminDashboard log: {}", e.getMessage(), e);
        }
    }

    /**
     * Обработка важных SQL логов Hibernate
     */
    @Async
    public void captureHibernateSqlLog(String level, String message, String loggerName) {
        try {
            if (!shouldCaptureSqlLog(message)) {
                return;
            }

            ActionType actionType = level.equals("ERROR") ? ActionType.SYSTEM_ERROR : ActionType.DATABASE_QUERY;
            String description = formatSqlLogDescription(message);
            String details = buildLogDetails(level, loggerName, message);

            userActivityLogService.logSystemActivityWithDetails(description, actionType, details);

            log.debug("Captured Hibernate SQL log: {}", description);

        } catch (Exception e) {
            log.error("Error capturing Hibernate SQL log: {}", e.getMessage(), e);
        }
    }

    /**
     * Обработка системных ошибок и предупреждений
     */
    @Async
    public void captureSystemErrorLog(String level, String message, String loggerName, Throwable throwable) {
        try {
            if (!shouldCaptureSystemError(level, message, loggerName)) {
                return;
            }

            ActionType actionType = determineActionTypeByLevel(level);
            String description = formatSystemErrorDescription(message, throwable);
            String details = buildErrorLogDetails(level, loggerName, message, throwable);

            userActivityLogService.logSystemActivity(description, actionType);

            log.debug("Captured system error log: {}", description);

        } catch (Exception e) {
            log.error("Error capturing system error log: {}", e.getMessage(), e);
        }
    }

    /**
     * Универсальный метод для захвата любых логов
     */
    @Async
    public void captureGenericLog(String level, String message, String loggerName, Throwable throwable) {
        try {
            // Определяем тип лога по имени logger'а
            if (loggerName.contains("DispatcherServlet")) {
                captureDispatcherServletLog(level, message, loggerName);
            } else if (loggerName.contains("BackgroundMetricsService")) {
                captureBackgroundMetricsLog(level, message, loggerName);
            } else if (loggerName.contains("AdminDashboardService")) {
                captureAdminDashboardLog(level, message, loggerName);
            } else if (loggerName.contains("hibernate") || loggerName.contains("SQL")) {
                captureHibernateSqlLog(level, message, loggerName);
            } else if (level.equals("ERROR") || level.equals("WARN")) {
                captureSystemErrorLog(level, message, loggerName, throwable);
            }

        } catch (Exception e) {
            log.error("Error in generic log capture: {}", e.getMessage(), e);
        }
    }

    // ==================== ФИЛЬТРЫ ДЛЯ ОПРЕДЕЛЕНИЯ ВАЖНОСТИ ЛОГОВ
    // ====================

    private boolean shouldCaptureDispatcherLog(String message) {
        // Захватываем только важные HTTP запросы
        return message.contains("admin") ||
                message.contains("payment") ||
                message.contains("webhook") ||
                message.contains("ERROR") ||
                message.contains("Exception");
    }

    private boolean shouldCaptureBackgroundLog(String message) {
        // Захватываем важные метрики и ошибки
        return message.contains("metrics") ||
                message.contains("performance") ||
                message.contains("ERROR") ||
                message.contains("WARNING") ||
                message.contains("cache");
    }

    private boolean shouldCaptureAdminLog(String message) {
        // Захватываем активности админки
        return message.contains("dashboard") ||
                message.contains("admin") ||
                message.contains("cache") ||
                message.contains("ERROR") ||
                message.contains("update");
    }

    private boolean shouldCaptureSqlLog(String message) {
        // Захватываем только важные SQL операции
        return message.toLowerCase().contains("insert") ||
                message.toLowerCase().contains("update") ||
                message.toLowerCase().contains("delete") ||
                message.contains("ERROR") ||
                message.contains("Exception") ||
                (message.toLowerCase().contains("select") &&
                        (message.contains("user_activity") || message.contains("orders")
                                || message.contains("payments")));
    }

    private boolean shouldCaptureSystemError(String level, String message, String loggerName) {
        // Захватываем все ошибки и предупреждения, исключаем debug логи
        return level.equals("ERROR") ||
                level.equals("WARN") ||
                (level.equals("INFO") && (message.contains("started") || message.contains("stopped")));
    }

    // ==================== ФОРМАТИРОВАНИЕ ОПИСАНИЙ ====================

    private String formatDispatcherLogDescription(String message) {
        Matcher urlMatcher = URL_PATTERN.matcher(message);
        if (urlMatcher.find()) {
            String method = urlMatcher.group(1);
            String url = urlMatcher.group(2);
            return String.format("HTTP %s %s", method, url);
        }
        return truncateMessage("HTTP запрос: " + message, 100);
    }

    private String formatBackgroundLogDescription(String message) {
        if (message.contains("metrics")) {
            return "Обновление метрик производительности";
        }
        if (message.contains("cache")) {
            return "Операция с кешем";
        }
        return truncateMessage("Фоновая задача: " + message, 100);
    }

    private String formatAdminLogDescription(String message) {
        if (message.contains("dashboard")) {
            return "Обновление админ-панели";
        }
        if (message.contains("cache")) {
            return "Операция с кешем админки";
        }
        return truncateMessage("Админ операция: " + message, 100);
    }

    private String formatSqlLogDescription(String message) {
        Matcher sqlMatcher = SQL_PATTERN.matcher(message);
        if (sqlMatcher.find()) {
            String operation = sqlMatcher.group(1).toUpperCase();
            String table = sqlMatcher.group(2);
            return String.format("SQL %s → %s", operation, table);
        }
        return truncateMessage("SQL запрос", 50);
    }

    private String formatSystemErrorDescription(String message, Throwable throwable) {
        if (throwable != null) {
            return String.format("Ошибка: %s", throwable.getClass().getSimpleName());
        }
        return truncateMessage("Системная ошибка: " + message, 100);
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    private ActionType determineActionTypeByLevel(String level) {
        return switch (level.toUpperCase()) {
            case "ERROR" -> ActionType.SYSTEM_ERROR;
            case "WARN" -> ActionType.SYSTEM_WARNING;
            default -> ActionType.SYSTEM_INFO;
        };
    }

    private String buildLogDetails(String level, String loggerName, String message) {
        return String.format("""
                {
                    "timestamp": "%s",
                    "level": "%s",
                    "logger": "%s",
                    "message": "%s",
                    "userId": "%s",
                    "orderId": "%s",
                    "thread": "%s"
                }
                """,
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                level,
                loggerName,
                escapeJson(message),
                extractUserId(message),
                extractOrderId(message),
                Thread.currentThread().getName());
    }

    private String buildErrorLogDetails(String level, String loggerName, String message, Throwable throwable) {
        String stackTrace = throwable != null ? throwable.getClass().getName() + ": " + throwable.getMessage() : "null";

        return String.format("""
                {
                    "timestamp": "%s",
                    "level": "%s",
                    "logger": "%s",
                    "message": "%s",
                    "exception": "%s",
                    "userId": "%s",
                    "orderId": "%s",
                    "errorCode": "%s",
                    "thread": "%s"
                }
                """,
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                level,
                loggerName,
                escapeJson(message),
                escapeJson(stackTrace),
                extractUserId(message),
                extractOrderId(message),
                extractErrorCode(message),
                Thread.currentThread().getName());
    }

    private String extractUserId(String message) {
        Matcher matcher = USER_ID_PATTERN.matcher(message);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String extractOrderId(String message) {
        Matcher matcher = ORDER_ID_PATTERN.matcher(message);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String extractErrorCode(String message) {
        Matcher matcher = ERROR_CODE_PATTERN.matcher(message);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String truncateMessage(String message, int maxLength) {
        if (message == null)
            return "";
        return message.length() > maxLength ? message.substring(0, maxLength - 3) + "..." : message;
    }

    private String escapeJson(String text) {
        if (text == null)
            return "";
        return text.replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}