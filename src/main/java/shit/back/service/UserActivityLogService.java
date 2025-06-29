package shit.back.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import shit.back.entity.OrderEntity;
import shit.back.entity.UserActivityLogEntity;
import shit.back.entity.UserActivityLogEntity.ActionType;
import shit.back.entity.UserActivityLogEntity.LogCategory;
import shit.back.entity.UserSessionEntity;
import shit.back.repository.UserActivityLogJpaRepository;
import shit.back.service.activity.UserActivityLoggingService;
import shit.back.service.activity.UserActivitySSEService;
import shit.back.service.activity.UserActivityStatisticsService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Основной сервис для работы с активностью пользователей
 * РЕФАКТОРИНГ: Разделен на специализированные сервисы следуя SRP
 * 
 * Делегирует к:
 * - UserActivityLoggingService - логирование активности
 * - UserActivitySSEService - SSE broadcast и real-time обновления
 * - UserActivityStatisticsService - статистика и аналитика
 */
@Slf4j
@Service
@Transactional
public class UserActivityLogService {

    @Autowired
    private UserActivityLogJpaRepository activityLogRepository;

    @Autowired
    private UserActivityLoggingService loggingService;

    @Autowired
    private UserActivitySSEService sseService;

    @Autowired
    private UserActivityStatisticsService statisticsService;

    // ==================== ДЕЛЕГАЦИЯ К ЛОГИРОВАНИЮ ====================

    /**
     * Логировать действие пользователя
     */
    @Async
    public void logUserActivity(Long userId, String username, String firstName, String lastName,
            ActionType actionType, String actionDescription) {
        loggingService.logUserActivity(userId, username, firstName, lastName, actionType, actionDescription);
    }

    /**
     * Логировать действие с информацией о заказе (СИНХРОННО для критичных операций)
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void logOrderActivity(Long userId, String username, String firstName, String lastName,
            ActionType actionType, String actionDescription,
            String orderId, BigDecimal orderAmount, Integer starCount, String paymentMethod) {
        loggingService.logOrderActivity(userId, username, firstName, lastName, actionType, actionDescription,
                orderId, orderAmount, starCount, paymentMethod);
    }

    /**
     * Асинхронная версия логирования заказов для обратной совместимости
     */
    @Async
    public void logOrderActivityAsync(Long userId, String username, String firstName, String lastName,
            ActionType actionType, String actionDescription,
            String orderId, BigDecimal orderAmount, Integer starCount, String paymentMethod) {
        loggingService.logOrderActivityAsync(userId, username, firstName, lastName, actionType, actionDescription,
                orderId, orderAmount, starCount, paymentMethod);
    }

    /**
     * Логировать изменение состояния сессии (СИНХРОННО для real-time обновлений)
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void logStateChange(UserSessionEntity userSession, String previousState, String newState) {
        loggingService.logStateChange(userSession, previousState, newState);
    }

    /**
     * Асинхронная версия логирования изменения состояния для обратной совместимости
     */
    @Async
    public void logStateChangeAsync(UserSessionEntity userSession, String previousState, String newState) {
        loggingService.logStateChangeAsync(userSession, previousState, newState);
    }

    /**
     * Логировать действие с заказом на основе OrderEntity
     */
    @Async
    public void logOrderAction(OrderEntity order, ActionType actionType, String description) {
        loggingService.logOrderAction(order, actionType, description);
    }

    /**
     * Логировать активность телеграм бота (СИНХРОННО для real-time обновлений)
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void logTelegramBotActivity(Long userId, String username, String firstName, String lastName,
            ActionType actionType, String actionDescription) {
        loggingService.logTelegramBotActivity(userId, username, firstName, lastName, actionType, actionDescription);
        log.debug("Active SSE connections: {}", sseService.getSseConnectionsInfo().get("totalConnections"));
    }

    /**
     * Асинхронная версия для обратной совместимости
     */
    @Async
    public void logTelegramBotActivityAsync(Long userId, String username, String firstName, String lastName,
            ActionType actionType, String actionDescription) {
        loggingService.logTelegramBotActivityAsync(userId, username, firstName, lastName, actionType,
                actionDescription);
    }

    /**
     * Логировать активность приложения
     */
    @Async
    public void logApplicationActivity(Long userId, String username, String firstName, String lastName,
            ActionType actionType, String actionDescription) {
        loggingService.logApplicationActivity(userId, username, firstName, lastName, actionType, actionDescription);
    }

    /**
     * Логировать системную активность
     */
    @Async
    public void logSystemActivity(String description, ActionType actionType) {
        loggingService.logSystemActivity(description, actionType);
    }

    /**
     * Логировать системную активность с дополнительными деталями
     */
    @Async
    public void logSystemActivityWithDetails(String description, ActionType actionType, String details) {
        loggingService.logSystemActivityWithDetails(description, actionType, details);
    }

    /**
     * Универсальный метод логирования с указанием категории
     */
    @Async
    public void logActivityWithCategory(Long userId, String username, String firstName, String lastName,
            ActionType actionType, String actionDescription, LogCategory logCategory) {
        loggingService.logActivityWithCategory(userId, username, firstName, lastName, actionType, actionDescription,
                logCategory);
    }

    // ==================== ДЕЛЕГАЦИЯ К СТАТИСТИКЕ ====================

    /**
     * Получить активности с фильтрами
     */
    @Transactional(readOnly = true)
    public Page<UserActivityLogEntity> getActivitiesWithFilters(
            boolean showAll,
            LocalDateTime fromTime,
            LocalDateTime toTime,
            List<ActionType> actionTypes,
            String searchTerm,
            Pageable pageable) {
        return statisticsService.getActivitiesWithFilters(showAll, fromTime, toTime, actionTypes, searchTerm, pageable);
    }

    /**
     * Получить только ключевые активности
     */
    @Transactional(readOnly = true)
    public Page<UserActivityLogEntity> getKeyActivities(Pageable pageable) {
        return statisticsService.getKeyActivities(pageable);
    }

    /**
     * Получить все активности
     */
    @Transactional(readOnly = true)
    public Page<UserActivityLogEntity> getAllActivities(Pageable pageable) {
        return statisticsService.getAllActivities(pageable);
    }

    /**
     * Получить активности пользователя
     */
    @Transactional(readOnly = true)
    public Page<UserActivityLogEntity> getUserActivities(Long userId, Pageable pageable) {
        return statisticsService.getUserActivities(userId, pageable);
    }

    /**
     * Получить активности по заказу
     */
    @Transactional(readOnly = true)
    public List<UserActivityLogEntity> getOrderActivities(String orderId) {
        return statisticsService.getOrderActivities(orderId);
    }

    /**
     * Получить последние активности для live feed
     */
    @Transactional(readOnly = true)
    public List<UserActivityLogEntity> getRecentActivities(int hours) {
        return statisticsService.getRecentActivities(hours);
    }

    /**
     * Получить активности по категории
     */
    @Transactional(readOnly = true)
    public Page<UserActivityLogEntity> getActivitiesByCategory(LogCategory logCategory, Pageable pageable) {
        return statisticsService.getActivitiesByCategory(logCategory, pageable);
    }

    /**
     * Получить активности телеграм бота
     */
    @Transactional(readOnly = true)
    public Page<UserActivityLogEntity> getTelegramBotActivities(int hours, Pageable pageable) {
        return statisticsService.getTelegramBotActivities(hours, pageable);
    }

    /**
     * Получить активности приложения
     */
    @Transactional(readOnly = true)
    public Page<UserActivityLogEntity> getApplicationActivities(int hours, Pageable pageable) {
        return statisticsService.getApplicationActivities(hours, pageable);
    }

    /**
     * Получить активности с расширенными фильтрами
     */
    @Transactional(readOnly = true)
    public Page<UserActivityLogEntity> getActivitiesWithCategoryFilters(
            boolean showAll,
            LocalDateTime fromTime,
            LocalDateTime toTime,
            List<ActionType> actionTypes,
            List<LogCategory> logCategories,
            String searchTerm,
            Pageable pageable) {
        return statisticsService.getActivitiesWithCategoryFilters(showAll, fromTime, toTime, actionTypes, logCategories,
                searchTerm, pageable);
    }

    /**
     * Получить статистику активности
     */
    @Transactional(readOnly = true)
    public UserActivityStatisticsService.ActivityStatistics getActivityStatistics(int hours) {
        return statisticsService.getActivityStatistics(hours);
    }

    /**
     * Получить статистику по категориям логов
     */
    @Transactional(readOnly = true)
    public UserActivityStatisticsService.CategoryStatistics getCategoryStatistics(int hours) {
        return statisticsService.getCategoryStatistics(hours);
    }

    /**
     * Получить dashboard для статусов оплаты
     */
    @Transactional(readOnly = true)
    public UserActivityStatisticsService.PaymentStatusDashboard getPaymentStatusDashboard() {
        return statisticsService.getPaymentStatusDashboard();
    }

    // ==================== ДЕЛЕГАЦИЯ К SSE ====================

    /**
     * Создать SSE соединение для live обновлений
     */
    public SseEmitter createSseConnection(String clientId) {
        return sseService.createSseConnection(clientId);
    }

    /**
     * Создать SSE соединение для live обновлений с фильтрацией по категории
     */
    public SseEmitter createSseConnection(String clientId, LogCategory category) {
        return sseService.createSseConnection(clientId, category);
    }

    /**
     * Получить информацию о подключенных SSE клиентах (для диагностики)
     */
    public Map<String, Object> getSseConnectionsInfo() {
        return sseService.getSseConnectionsInfo();
    }

    // ==================== BACKWARD COMPATIBILITY DATA TRANSFER OBJECTS
    // ====================
    // Оставляем для обратной совместимости, но делегируем к статистическому сервису

    @lombok.Data
    @lombok.Builder
    public static class ActivityStatistics {
        private long totalActivities;
        private long keyActivities;
        private List<ActionTypeStat> actionTypeStats;
        private List<UserActivityStat> mostActiveUsers;
        private List<PaymentMethodStat> paymentMethodStats;
        private int periodHours;
    }

    @lombok.Data
    @lombok.Builder
    public static class PaymentStatusDashboard {
        private List<UserActivityLogEntity> completedPayments;
        private List<UserActivityLogEntity> pendingPayments;
        private List<UserActivityLogEntity> failedPayments;
        private List<UserActivityLogEntity> cancelledOrders;
        private List<StuckUser> stuckUsers;
        private BigDecimal totalSalesAmount;
        private BigDecimal averageOrderAmount;
        private double successRate;
        private String mostPopularPackage;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ActionTypeStat {
        private ActionType actionType;
        private Long count;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class UserActivityStat {
        private Long userId;
        private String username;
        private Long activityCount;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class PaymentMethodStat {
        private String paymentMethod;
        private Long count;
        private BigDecimal totalAmount;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class StuckUser {
        private Long userId;
        private String username;
        private LocalDateTime lastActivity;
    }

    @lombok.Data
    @lombok.Builder
    public static class CategoryStatistics {
        private long telegramBotActivities;
        private long applicationActivities;
        private long systemActivities;
        private long telegramBotKeyActivities;
        private long applicationKeyActivities;
        private long systemKeyActivities;
        private List<CategoryStat> categoryStats;
        private List<CategoryActionStat> categoryActionStats;
        private int periodHours;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class CategoryStat {
        private LogCategory logCategory;
        private Long count;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class CategoryActionStat {
        private LogCategory logCategory;
        private ActionType actionType;
        private Long count;
    }
}
