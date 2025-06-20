package shit.back.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Класс для хранения SSE соединения с информацией о выбранной категории фильтра
 */
@lombok.Data
@lombok.AllArgsConstructor
class CategorySseConnection {
    private final SseEmitter emitter;
    private final LogCategory category; // null означает "ALL" (все категории)

    public boolean shouldReceiveActivity(UserActivityLogEntity activity) {
        // Если category == null, значит клиент хочет получать все активности
        if (category == null) {
            return true;
        }

        // Иначе отправляем только активности выбранной категории
        return category.equals(activity.getLogCategory());
    }
}

@Slf4j
@Service
@Transactional
public class UserActivityLogService {

    @Autowired
    private UserActivityLogJpaRepository activityLogRepository;

    // SSE connections для real-time обновлений с поддержкой категорий
    private final Map<String, CategorySseConnection> sseConnections = new ConcurrentHashMap<>();
    private final List<UserActivityLogEntity> recentActivities = new CopyOnWriteArrayList<>();

    // ==================== ОСНОВНЫЕ МЕТОДЫ ЛОГИРОВАНИЯ ====================

    /**
     * Логировать действие пользователя
     */
    @Async
    public void logUserActivity(Long userId, String username, String firstName, String lastName,
            ActionType actionType, String actionDescription) {
        try {
            UserActivityLogEntity activity = new UserActivityLogEntity(
                    userId, username, firstName, lastName, actionType, actionDescription);

            UserActivityLogEntity saved = activityLogRepository.save(activity);

            // Добавить в recent activities для live feed
            addToRecentActivities(saved);

            // Отправить обновление через SSE
            broadcastActivity(saved);

            log.debug("Logged activity: {} for user {}", actionType, username);
        } catch (Exception e) {
            log.error("Error logging user activity: {}", e.getMessage(), e);
        }
    }

    /**
     * Логировать действие с информацией о заказе (СИНХРОННО для критичных операций)
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void logOrderActivity(Long userId, String username, String firstName, String lastName,
            ActionType actionType, String actionDescription,
            String orderId, BigDecimal orderAmount, Integer starCount, String paymentMethod) {
        long startTime = System.currentTimeMillis();
        try {
            UserActivityLogEntity activity = new UserActivityLogEntity(
                    userId, username, firstName, lastName, actionType, actionDescription)
                    .withOrderInfo(orderId, orderAmount, starCount)
                    .withPaymentMethod(paymentMethod);

            UserActivityLogEntity saved = activityLogRepository.save(activity);

            addToRecentActivities(saved);

            long dbTime = System.currentTimeMillis();
            log.debug("Order activity saved to DB in {}ms", dbTime - startTime);

            broadcastActivity(saved);

            long totalTime = System.currentTimeMillis();
            log.debug("Order activity broadcast completed in {}ms total", totalTime - startTime);

        } catch (Exception e) {
            log.error("Error logging order activity: {}", e.getMessage(), e);
        }
    }

    /**
     * Асинхронная версия логирования заказов для обратной совместимости
     */
    @Async
    public void logOrderActivityAsync(Long userId, String username, String firstName, String lastName,
            ActionType actionType, String actionDescription,
            String orderId, BigDecimal orderAmount, Integer starCount, String paymentMethod) {
        logOrderActivity(userId, username, firstName, lastName, actionType, actionDescription,
                orderId, orderAmount, starCount, paymentMethod);
    }

    /**
     * Логировать изменение состояния сессии (СИНХРОННО для real-time обновлений)
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void logStateChange(UserSessionEntity userSession, String previousState, String newState) {
        long startTime = System.currentTimeMillis();
        try {
            ActionType actionType = determineActionTypeByState(newState);
            String description = String.format("Изменил состояние с %s на %s", previousState, newState);

            UserActivityLogEntity activity = new UserActivityLogEntity(
                    userSession.getUserId(),
                    userSession.getUsername(),
                    userSession.getFirstName(),
                    userSession.getLastName(),
                    actionType,
                    description).withStateChange(previousState, newState);

            UserActivityLogEntity saved = activityLogRepository.save(activity);

            addToRecentActivities(saved);

            long dbTime = System.currentTimeMillis();
            log.debug("State change activity saved to DB in {}ms", dbTime - startTime);

            broadcastActivity(saved);

            long totalTime = System.currentTimeMillis();
            log.debug("State change activity broadcast completed in {}ms total", totalTime - startTime);

        } catch (Exception e) {
            log.error("Error logging state change: {}", e.getMessage(), e);
        }
    }

    /**
     * Асинхронная версия логирования изменения состояния для обратной совместимости
     */
    @Async
    public void logStateChangeAsync(UserSessionEntity userSession, String previousState, String newState) {
        logStateChange(userSession, previousState, newState);
    }

    /**
     * Логировать действие с заказом на основе OrderEntity
     */
    @Async
    public void logOrderAction(OrderEntity order, ActionType actionType, String description) {
        try {
            UserActivityLogEntity activity = new UserActivityLogEntity(
                    order.getUserId(),
                    order.getUsername(),
                    null, null, // firstName, lastName не доступны в OrderEntity
                    actionType,
                    description)
                    .withOrderInfo(order.getOrderId(), order.getFinalAmount(), order.getStarCount())
                    .withPaymentMethod(order.getPaymentMethod());

            UserActivityLogEntity saved = activityLogRepository.save(activity);

            addToRecentActivities(saved);
            broadcastActivity(saved);

        } catch (Exception e) {
            log.error("Error logging order action: {}", e.getMessage(), e);
        }
    }

    // ==================== НОВЫЕ МЕТОДЫ ДЛЯ КАТЕГОРИЗИРОВАННОГО ЛОГИРОВАНИЯ
    // ====================

    /**
     * Логировать активность телеграм бота (СИНХРОННО для real-time обновлений)
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void logTelegramBotActivity(Long userId, String username, String firstName, String lastName,
            ActionType actionType, String actionDescription) {
        long startTime = System.currentTimeMillis();
        try {
            UserActivityLogEntity activity = new UserActivityLogEntity(
                    userId, username, firstName, lastName, actionType, actionDescription)
                    .withLogCategory(LogCategory.TELEGRAM_BOT);

            UserActivityLogEntity saved = activityLogRepository.save(activity);

            addToRecentActivities(saved);

            long dbTime = System.currentTimeMillis();
            log.debug("Telegram bot activity saved to DB in {}ms", dbTime - startTime);

            // Немедленная отправка через SSE
            broadcastActivity(saved);

            long totalTime = System.currentTimeMillis();
            log.debug("Telegram bot activity broadcast completed in {}ms total", totalTime - startTime);
            log.debug("Active SSE connections: {}", sseConnections.size());

        } catch (Exception e) {
            log.error("Error logging Telegram bot activity: {}", e.getMessage(), e);
        }
    }

    /**
     * Асинхронная версия для обратной совместимости
     */
    @Async
    public void logTelegramBotActivityAsync(Long userId, String username, String firstName, String lastName,
            ActionType actionType, String actionDescription) {
        logTelegramBotActivity(userId, username, firstName, lastName, actionType, actionDescription);
    }

    /**
     * Логировать активность приложения
     */
    @Async
    public void logApplicationActivity(Long userId, String username, String firstName, String lastName,
            ActionType actionType, String actionDescription) {
        try {
            UserActivityLogEntity activity = new UserActivityLogEntity(
                    userId, username, firstName, lastName, actionType, actionDescription)
                    .withLogCategory(LogCategory.APPLICATION);

            UserActivityLogEntity saved = activityLogRepository.save(activity);

            addToRecentActivities(saved);
            broadcastActivity(saved);

            log.debug("Logged application activity: {} for user {}", actionType, username);
        } catch (Exception e) {
            log.error("Error logging application activity: {}", e.getMessage(), e);
        }
    }

    /**
     * Логировать системную активность
     */
    @Async
    public void logSystemActivity(String description, ActionType actionType) {
        try {
            UserActivityLogEntity activity = new UserActivityLogEntity(
                    0L, "SYSTEM", actionType, description).withLogCategory(LogCategory.SYSTEM);

            UserActivityLogEntity saved = activityLogRepository.save(activity);

            addToRecentActivities(saved);
            broadcastActivity(saved);

            log.debug("Logged system activity: {}", actionType);
        } catch (Exception e) {
            log.error("Error logging system activity: {}", e.getMessage(), e);
        }
    }

    /**
     * Логировать системную активность с дополнительными деталями
     */
    @Async
    public void logSystemActivityWithDetails(String description, ActionType actionType, String details) {
        try {
            UserActivityLogEntity activity = new UserActivityLogEntity(
                    0L, "SYSTEM", actionType, description)
                    .withLogCategory(LogCategory.APPLICATION)
                    .withDetails(details);

            UserActivityLogEntity saved = activityLogRepository.save(activity);

            addToRecentActivities(saved);
            broadcastActivity(saved);

            log.debug("Logged system activity with details: {}", actionType);
        } catch (Exception e) {
            log.error("Error logging system activity with details: {}", e.getMessage(), e);
        }
    }

    /**
     * Универсальный метод логирования с указанием категории
     */
    @Async
    public void logActivityWithCategory(Long userId, String username, String firstName, String lastName,
            ActionType actionType, String actionDescription, LogCategory logCategory) {
        try {
            UserActivityLogEntity activity = new UserActivityLogEntity(
                    userId, username, firstName, lastName, actionType, actionDescription, logCategory);

            UserActivityLogEntity saved = activityLogRepository.save(activity);

            addToRecentActivities(saved);
            broadcastActivity(saved);

            log.debug("Logged {} activity: {} for user {}", logCategory, actionType, username);
        } catch (Exception e) {
            log.error("Error logging categorized activity: {}", e.getMessage(), e);
        }
    }

    // ==================== МЕТОДЫ ПОЛУЧЕНИЯ ДАННЫХ ====================

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

        return activityLogRepository.findWithFilters(
                showAll, fromTime, toTime, actionTypes, searchTerm, pageable);
    }

    /**
     * Получить только ключевые активности
     */
    @Transactional(readOnly = true)
    public Page<UserActivityLogEntity> getKeyActivities(Pageable pageable) {
        return activityLogRepository.findKeyActionsOrderByTimestampDesc(pageable);
    }

    /**
     * Получить все активности
     */
    @Transactional(readOnly = true)
    public Page<UserActivityLogEntity> getAllActivities(Pageable pageable) {
        return activityLogRepository.findAllByOrderByTimestampDesc(pageable);
    }

    /**
     * Получить активности пользователя
     */
    @Transactional(readOnly = true)
    public Page<UserActivityLogEntity> getUserActivities(Long userId, Pageable pageable) {
        return activityLogRepository.findByUserIdOrderByTimestampDesc(userId, pageable);
    }

    /**
     * Получить активности по заказу
     */
    @Transactional(readOnly = true)
    public List<UserActivityLogEntity> getOrderActivities(String orderId) {
        return activityLogRepository.findByOrderIdOrderByTimestampDesc(orderId);
    }

    /**
     * Получить последние активности для live feed
     */
    @Transactional(readOnly = true)
    public List<UserActivityLogEntity> getRecentActivities(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return activityLogRepository.findRecentKeyActivities(since);
    }

    // ==================== МЕТОДЫ ПОЛУЧЕНИЯ ДАННЫХ ПО КАТЕГОРИЯМ
    // ====================

    /**
     * Получить активности по категории
     */
    @Transactional(readOnly = true)
    public Page<UserActivityLogEntity> getActivitiesByCategory(LogCategory logCategory, Pageable pageable) {
        return activityLogRepository.findByLogCategoryOrderByTimestampDesc(logCategory, pageable);
    }

    /**
     * Получить активности телеграм бота
     */
    @Transactional(readOnly = true)
    public Page<UserActivityLogEntity> getTelegramBotActivities(int hours, Pageable pageable) {
        LocalDateTime fromTime = LocalDateTime.now().minusHours(hours);
        return activityLogRepository.findTelegramBotActivities(fromTime, pageable);
    }

    /**
     * Получить активности приложения
     */
    @Transactional(readOnly = true)
    public Page<UserActivityLogEntity> getApplicationActivities(int hours, Pageable pageable) {
        LocalDateTime fromTime = LocalDateTime.now().minusHours(hours);
        return activityLogRepository.findApplicationActivities(fromTime, pageable);
    }

    /**
     * Получить активности с расширенными фильтрами (включая категории)
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

        return activityLogRepository.findWithFiltersAndCategories(
                showAll, fromTime, toTime, actionTypes, logCategories, searchTerm, pageable);
    }

    // ==================== СТАТИСТИКА И АНАЛИТИКА ====================

    /**
     * Получить статистику активности
     */
    @Transactional(readOnly = true)
    public ActivityStatistics getActivityStatistics(int hours) {
        LocalDateTime fromTime = LocalDateTime.now().minusHours(hours);

        long totalActivities = activityLogRepository.countByTimestampAfter(fromTime);
        long keyActivities = activityLogRepository.countByIsKeyActionTrueAndTimestampAfter(fromTime);

        List<Object[]> actionStats = activityLogRepository.getKeyActionTypeStatistics(fromTime);
        List<Object[]> userStats = activityLogRepository.getMostActiveUsers(fromTime);
        List<Object[]> paymentStats = activityLogRepository.getPaymentMethodStatistics(fromTime);

        return ActivityStatistics.builder()
                .totalActivities(totalActivities)
                .keyActivities(keyActivities)
                .actionTypeStats(convertActionTypeStats(actionStats))
                .mostActiveUsers(convertUserStats(userStats))
                .paymentMethodStats(convertPaymentStats(paymentStats))
                .periodHours(hours)
                .build();
    }

    /**
     * Получить статистику по категориям логов
     */
    @Transactional(readOnly = true)
    public CategoryStatistics getCategoryStatistics(int hours) {
        LocalDateTime fromTime = LocalDateTime.now().minusHours(hours);

        long telegramBotActivities = activityLogRepository.countByLogCategoryAndTimestampAfter(LogCategory.TELEGRAM_BOT,
                fromTime);
        long applicationActivities = activityLogRepository.countByLogCategoryAndTimestampAfter(LogCategory.APPLICATION,
                fromTime);
        long systemActivities = activityLogRepository.countByLogCategoryAndTimestampAfter(LogCategory.SYSTEM, fromTime);

        // Проверяем общее количество логов для валидации
        long totalLogsInPeriod = activityLogRepository.countByTimestampAfter(fromTime);

        log.debug("CategoryStatistics: period={}h, total={}, telegram={}, app={}, system={}",
                hours, totalLogsInPeriod, telegramBotActivities, applicationActivities, systemActivities);

        long telegramBotKeyActivities = activityLogRepository
                .countByLogCategoryAndIsKeyActionTrueAndTimestampAfter(LogCategory.TELEGRAM_BOT, fromTime);
        long applicationKeyActivities = activityLogRepository
                .countByLogCategoryAndIsKeyActionTrueAndTimestampAfter(LogCategory.APPLICATION, fromTime);
        long systemKeyActivities = activityLogRepository
                .countByLogCategoryAndIsKeyActionTrueAndTimestampAfter(LogCategory.SYSTEM, fromTime);

        List<Object[]> categoryStats = activityLogRepository.getLogCategoryStatistics(fromTime);
        List<Object[]> categoryActionStats = activityLogRepository.getCategoryActionTypeStatistics(fromTime);

        return CategoryStatistics.builder()
                .telegramBotActivities(telegramBotActivities)
                .applicationActivities(applicationActivities)
                .systemActivities(systemActivities)
                .telegramBotKeyActivities(telegramBotKeyActivities)
                .applicationKeyActivities(applicationKeyActivities)
                .systemKeyActivities(systemKeyActivities)
                .categoryStats(convertCategoryStats(categoryStats))
                .categoryActionStats(convertCategoryActionStats(categoryActionStats))
                .periodHours(hours)
                .build();
    }

    /**
     * Получить dashboard для статусов оплаты
     */
    @Transactional(readOnly = true)
    public PaymentStatusDashboard getPaymentStatusDashboard() {
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);

        // Получить активности по оплатам за сегодня
        List<UserActivityLogEntity> completedPayments = activityLogRepository
                .findByActionTypeOrderByTimestampDesc(ActionType.PAYMENT_COMPLETED)
                .stream()
                .filter(a -> a.getTimestamp().isAfter(today))
                .collect(Collectors.toList());

        List<UserActivityLogEntity> pendingPayments = activityLogRepository
                .findByActionTypeOrderByTimestampDesc(ActionType.PAYMENT_INITIATED)
                .stream()
                .filter(a -> a.getTimestamp().isAfter(today))
                .collect(Collectors.toList());

        List<UserActivityLogEntity> failedPayments = activityLogRepository
                .findByActionTypeOrderByTimestampDesc(ActionType.PAYMENT_FAILED)
                .stream()
                .filter(a -> a.getTimestamp().isAfter(today))
                .collect(Collectors.toList());

        List<UserActivityLogEntity> cancelledOrders = activityLogRepository
                .findByActionTypeOrderByTimestampDesc(ActionType.ORDER_CANCELLED)
                .stream()
                .filter(a -> a.getTimestamp().isAfter(today))
                .collect(Collectors.toList());

        // Получить пользователей, зависших в оплате (более 30 минут)
        LocalDateTime stuckCutoff = LocalDateTime.now().minusMinutes(30);
        List<Object[]> stuckUsers = activityLogRepository.findUsersStuckInPayment(stuckCutoff);

        // Подсчитать статистику
        BigDecimal totalSalesAmount = completedPayments.stream()
                .filter(a -> a.getOrderAmount() != null)
                .map(UserActivityLogEntity::getOrderAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        double successRate = 0.0;
        if (!pendingPayments.isEmpty() || !completedPayments.isEmpty()) {
            int totalAttempts = completedPayments.size() + failedPayments.size();
            if (totalAttempts > 0) {
                successRate = (double) completedPayments.size() / totalAttempts * 100.0;
            }
        }

        return PaymentStatusDashboard.builder()
                .completedPayments(completedPayments)
                .pendingPayments(pendingPayments)
                .failedPayments(failedPayments)
                .cancelledOrders(cancelledOrders)
                .stuckUsers(convertStuckUsers(stuckUsers))
                .totalSalesAmount(totalSalesAmount)
                .averageOrderAmount(calculateAverageOrderAmount(completedPayments))
                .successRate(successRate)
                .mostPopularPackage(findMostPopularPackage(completedPayments))
                .build();
    }

    // ==================== REAL-TIME SSE МЕТОДЫ ====================

    /**
     * Создать SSE соединение для live обновлений
     */
    public SseEmitter createSseConnection(String clientId) {
        return createSseConnection(clientId, null); // null означает "ALL" категории
    }

    /**
     * Создать SSE соединение для live обновлений с фильтрацией по категории
     */
    public SseEmitter createSseConnection(String clientId, LogCategory category) {
        long connectionStart = System.currentTimeMillis();
        SseEmitter emitter = new SseEmitter(300000L); // 5 минут timeout

        CategorySseConnection connection = new CategorySseConnection(emitter, category);
        sseConnections.put(clientId, connection);

        log.info("SSE connection created for client: {} with category: {}. Total connections: {}",
                clientId, category != null ? category : "ALL", sseConnections.size());

        // ДИАГНОСТИКА: Лог создания соединения
        log.debug("LIVE_FEED_DEBUG: SSE connection created - clientId={}, category={}, timestamp={}",
                clientId, category != null ? category : "ALL", System.currentTimeMillis());

        emitter.onCompletion(() -> {
            sseConnections.remove(clientId);
            log.debug("SSE connection completed for client: {}. Remaining: {}", clientId, sseConnections.size());
        });

        emitter.onTimeout(() -> {
            sseConnections.remove(clientId);
            log.debug("SSE connection timeout for client: {}. Remaining: {}", clientId, sseConnections.size());
        });

        emitter.onError(e -> {
            log.warn("SSE error for client {}: {}. Removing connection.", clientId, e.getMessage());
            sseConnections.remove(clientId);
        });

        // Отправить начальные данные
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{ \"message\": \"Connected to activity stream\", \"clientId\": \"" + clientId
                            + "\", \"serverTime\": \"" + System.currentTimeMillis() + "\" }"));

            // Отправить последние активности
            List<UserActivityLogEntity> recent = getRecentActivities(1);
            log.debug("Sending {} recent activities to new SSE client {}", recent.size(), clientId);

            for (UserActivityLogEntity activity : recent) {
                emitter.send(SseEmitter.event()
                        .name("activity")
                        .data(convertActivityToJson(activity)));
            }

            long setupTime = System.currentTimeMillis() - connectionStart;
            log.debug("SSE connection setup completed in {}ms for client: {}", setupTime, clientId);

        } catch (Exception e) {
            log.error("Error sending initial SSE data to client {}: {}", clientId, e.getMessage());
            sseConnections.remove(clientId);
        }

        return emitter;
    }

    /**
     * Отправить активность всем подключенным клиентам с фильтрацией по категориям
     */
    private void broadcastActivity(UserActivityLogEntity activity) {
        long broadcastStart = System.currentTimeMillis();

        if (sseConnections.isEmpty()) {
            log.debug("No SSE connections to broadcast to");
            return;
        }

        // ДИАГНОСТИКА: Детальное логирование broadcast
        log.debug("LIVE_FEED_DEBUG: Broadcasting activity - type={}, category={}, connections={}, description={}",
                activity.getActionType(), activity.getLogCategory(), sseConnections.size(),
                activity.getActionDescription());

        log.debug("Broadcasting activity to {} SSE connections: {} - {}",
                sseConnections.size(), activity.getActionType(), activity.getActionDescription());

        String activityJson = convertActivityToJson(activity);
        int successCount = 0;
        int failureCount = 0;
        int filteredCount = 0;

        Iterator<Map.Entry<String, CategorySseConnection>> iterator = sseConnections.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CategorySseConnection> entry = iterator.next();
            CategorySseConnection connection = entry.getValue();
            String clientId = entry.getKey();

            // ФИЛЬТРАЦИЯ: Проверяем должен ли клиент получить эту активность
            if (!connection.shouldReceiveActivity(activity)) {
                filteredCount++;
                log.trace(
                        "LIVE_FEED_DEBUG: Activity filtered for client {} - category filter: {}, activity category: {}",
                        clientId, connection.getCategory(), activity.getLogCategory());
                continue;
            }

            try {
                connection.getEmitter().send(SseEmitter.event()
                        .name("activity")
                        .data(activityJson));
                successCount++;

                // ДИАГНОСТИКА: Лог успешной отправки
                log.trace("LIVE_FEED_DEBUG: Activity sent to client {} - type={}, category={}, client filter: {}",
                        clientId, activity.getActionType(), activity.getLogCategory(),
                        connection.getCategory() != null ? connection.getCategory() : "ALL");
            } catch (Exception e) {
                log.warn("Failed to send SSE to client {}: {}", clientId, e.getMessage());
                iterator.remove();
                failureCount++;
            }
        }

        long broadcastTime = System.currentTimeMillis() - broadcastStart;
        log.debug("Broadcast completed in {}ms: {} successful, {} failed, {} filtered by category",
                broadcastTime, successCount, failureCount, filteredCount);
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    private void addToRecentActivities(UserActivityLogEntity activity) {
        recentActivities.add(0, activity);

        // Оставляем только последние 100 активностей в памяти
        if (recentActivities.size() > 100) {
            recentActivities.subList(100, recentActivities.size()).clear();
        }
    }

    private ActionType determineActionTypeByState(String state) {
        return switch (state) {
            case "IDLE" -> ActionType.SESSION_START;
            case "SELECTING_PACKAGE" -> ActionType.PACKAGE_VIEWED;
            case "CONFIRMING_ORDER" -> ActionType.PACKAGE_SELECTED;
            case "AWAITING_PAYMENT" -> ActionType.ORDER_CREATED;
            case "PAYMENT_PROCESSING" -> ActionType.PAYMENT_INITIATED;
            case "COMPLETED" -> ActionType.ORDER_COMPLETED;
            case "CANCELLED" -> ActionType.ORDER_CANCELLED;
            case "EXPIRED" -> ActionType.SESSION_EXPIRED;
            default -> ActionType.STATE_CHANGED;
        };
    }

    private String convertActivityToJson(UserActivityLogEntity activity) {
        return String.format("""
                {
                    "id": %d,
                    "userId": %d,
                    "username": "%s",
                    "displayName": "%s",
                    "actionType": "%s",
                    "actionIcon": "%s",
                    "actionDescription": "%s",
                    "logCategory": "%s",
                    "logCategoryDisplay": "%s",
                    "actionDisplayNameWithCategory": "%s",
                    "timestamp": "%s",
                    "formattedTimestamp": "%s",
                    "orderId": "%s",
                    "orderInfo": "%s",
                    "stateChange": "%s",
                    "priorityClass": "%s",
                    "isKeyAction": %b,
                    "isTelegramBotActivity": %b,
                    "isApplicationActivity": %b,
                    "isSystemActivity": %b
                }
                """,
                activity.getId(),
                activity.getUserId(),
                activity.getUsername() != null ? activity.getUsername() : "",
                activity.getDisplayName(),
                activity.getActionType(),
                activity.getActionIcon(),
                activity.getActionDescription() != null ? activity.getActionDescription() : "",
                activity.getLogCategory(),
                activity.getLogCategoryDisplay(),
                activity.getActionDisplayNameWithCategory(),
                activity.getTimestamp(),
                activity.getFormattedTimestamp(),
                activity.getOrderId() != null ? activity.getOrderId() : "",
                activity.getOrderDisplayInfo(),
                activity.getStateChangeDisplay(),
                activity.getPriorityClass(),
                activity.getIsKeyAction(),
                activity.isTelegramBotActivity(),
                activity.isApplicationActivity(),
                activity.isSystemActivity());
    }

    private List<ActionTypeStat> convertActionTypeStats(List<Object[]> stats) {
        return stats.stream()
                .map(row -> new ActionTypeStat((ActionType) row[0], (Long) row[1]))
                .collect(Collectors.toList());
    }

    private List<UserActivityStat> convertUserStats(List<Object[]> stats) {
        return stats.stream()
                .map(row -> new UserActivityStat((Long) row[0], (String) row[1], (Long) row[2]))
                .collect(Collectors.toList());
    }

    private List<PaymentMethodStat> convertPaymentStats(List<Object[]> stats) {
        return stats.stream()
                .map(row -> new PaymentMethodStat(
                        (String) row[0],
                        (Long) row[1],
                        (BigDecimal) row[2]))
                .collect(Collectors.toList());
    }

    private List<StuckUser> convertStuckUsers(List<Object[]> stuckUsers) {
        return stuckUsers.stream()
                .map(row -> new StuckUser(
                        (Long) row[0],
                        (String) row[1],
                        (LocalDateTime) row[2]))
                .collect(Collectors.toList());
    }

    private BigDecimal calculateAverageOrderAmount(List<UserActivityLogEntity> completedPayments) {
        if (completedPayments.isEmpty())
            return BigDecimal.ZERO;

        List<BigDecimal> amounts = completedPayments.stream()
                .map(UserActivityLogEntity::getOrderAmount)
                .filter(Objects::nonNull)
                .toList();

        if (amounts.isEmpty())
            return BigDecimal.ZERO;

        BigDecimal sum = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(amounts.size()), 2, BigDecimal.ROUND_HALF_UP);
    }

    private String findMostPopularPackage(List<UserActivityLogEntity> completedPayments) {
        Map<Integer, Long> packageCounts = completedPayments.stream()
                .filter(a -> a.getStarCount() != null)
                .collect(Collectors.groupingBy(
                        UserActivityLogEntity::getStarCount,
                        Collectors.counting()));

        return packageCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())  
                .map(entry -> entry.getKey() + "⭐ Package")
                .orElse("N/A");
    }

    // ==================== DATA TRANSFER OBJECTS ====================

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

    // ==================== МЕТОДЫ КОНВЕРТАЦИИ ДЛЯ КАТЕГОРИЙ ====================

    private List<CategoryStat> convertCategoryStats(List<Object[]> stats) {
        return stats.stream()
                .map(row -> new CategoryStat((LogCategory) row[0], (Long) row[1]))
                .collect(Collectors.toList());
    }

    private List<CategoryActionStat> convertCategoryActionStats(List<Object[]> stats) {
        return stats.stream()
                .map(row -> new CategoryActionStat(
                        (LogCategory) row[0],
                        (ActionType) row[1],
                        (Long) row[2]))
                .collect(Collectors.toList());
    }

    // ==================== ДИАГНОСТИКА ФИЛЬТРАЦИИ ====================

    /**
     * Получить информацию о подключенных SSE клиентах (для диагностики)
     */
    public Map<String, Object> getSseConnectionsInfo() {
        Map<String, Object> info = new HashMap<>();

        Map<String, String> connections = new HashMap<>();
        for (Map.Entry<String, CategorySseConnection> entry : sseConnections.entrySet()) {
            CategorySseConnection connection = entry.getValue();
            connections.put(entry.getKey(),
                    connection.getCategory() != null ? connection.getCategory().toString() : "ALL");
        }

        info.put("totalConnections", sseConnections.size());
        info.put("connections", connections);
        info.put("timestamp", LocalDateTime.now());

        log.debug("SSE connections info requested: {} active connections", sseConnections.size());

        return info;
    }
}
