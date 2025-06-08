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
import shit.back.entity.UserSessionEntity;
import shit.back.repository.UserActivityLogJpaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class UserActivityLogService {
    
    @Autowired
    private UserActivityLogJpaRepository activityLogRepository;
    
    // SSE connections для real-time обновлений
    private final Map<String, SseEmitter> sseConnections = new ConcurrentHashMap<>();
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
                userId, username, firstName, lastName, actionType, actionDescription
            );
            
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
     * Логировать действие с информацией о заказе
     */
    @Async
    public void logOrderActivity(Long userId, String username, String firstName, String lastName,
                               ActionType actionType, String actionDescription,
                               String orderId, BigDecimal orderAmount, Integer starCount, String paymentMethod) {
        try {
            UserActivityLogEntity activity = new UserActivityLogEntity(
                userId, username, firstName, lastName, actionType, actionDescription
            )
            .withOrderInfo(orderId, orderAmount, starCount)
            .withPaymentMethod(paymentMethod);
            
            UserActivityLogEntity saved = activityLogRepository.save(activity);
            
            addToRecentActivities(saved);
            broadcastActivity(saved);
            
            log.debug("Logged order activity: {} for user {} order {}", actionType, username, orderId);
        } catch (Exception e) {
            log.error("Error logging order activity: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Логировать изменение состояния сессии
     */
    @Async
    public void logStateChange(UserSessionEntity userSession, String previousState, String newState) {
        try {
            ActionType actionType = determineActionTypeByState(newState);
            String description = String.format("Изменил состояние с %s на %s", previousState, newState);
            
            UserActivityLogEntity activity = new UserActivityLogEntity(
                userSession.getUserId(), 
                userSession.getUsername(),
                userSession.getFirstName(),
                userSession.getLastName(),
                actionType,
                description
            ).withStateChange(previousState, newState);
            
            UserActivityLogEntity saved = activityLogRepository.save(activity);
            
            addToRecentActivities(saved);
            broadcastActivity(saved);
            
        } catch (Exception e) {
            log.error("Error logging state change: {}", e.getMessage(), e);
        }
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
                description
            )
            .withOrderInfo(order.getOrderId(), order.getFinalAmount(), order.getStarCount())
            .withPaymentMethod(order.getPaymentMethod());
            
            UserActivityLogEntity saved = activityLogRepository.save(activity);
            
            addToRecentActivities(saved);
            broadcastActivity(saved);
            
        } catch (Exception e) {
            log.error("Error logging order action: {}", e.getMessage(), e);
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
            showAll, fromTime, toTime, actionTypes, searchTerm, pageable
        );
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
        SseEmitter emitter = new SseEmitter(300000L); // 5 минут timeout
        
        sseConnections.put(clientId, emitter);
        
        emitter.onCompletion(() -> sseConnections.remove(clientId));
        emitter.onTimeout(() -> sseConnections.remove(clientId));
        emitter.onError(e -> {
            log.warn("SSE error for client {}: {}", clientId, e.getMessage());
            sseConnections.remove(clientId);
        });
        
        // Отправить начальные данные
        try {
            emitter.send(SseEmitter.event()
                .name("connected")
                .data("{ \"message\": \"Connected to activity stream\", \"clientId\": \"" + clientId + "\" }"));
            
            // Отправить последние активности
            List<UserActivityLogEntity> recent = getRecentActivities(1);
            for (UserActivityLogEntity activity : recent) {
                emitter.send(SseEmitter.event()
                    .name("activity")
                    .data(convertActivityToJson(activity)));
            }
            
        } catch (Exception e) {
            log.error("Error sending initial SSE data: {}", e.getMessage());
            sseConnections.remove(clientId);
        }
        
        return emitter;
    }
    
    /**
     * Отправить активность всем подключенным клиентам
     */
    private void broadcastActivity(UserActivityLogEntity activity) {
        if (sseConnections.isEmpty()) return;
        
        String activityJson = convertActivityToJson(activity);
        
        Iterator<Map.Entry<String, SseEmitter>> iterator = sseConnections.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, SseEmitter> entry = iterator.next();
            SseEmitter emitter = entry.getValue();
            
            try {
                emitter.send(SseEmitter.event()
                    .name("activity")
                    .data(activityJson));
            } catch (Exception e) {
                log.warn("Failed to send SSE to client {}: {}", entry.getKey(), e.getMessage());
                iterator.remove();
            }
        }
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
                "timestamp": "%s",
                "formattedTimestamp": "%s",
                "orderId": "%s",
                "orderInfo": "%s",
                "stateChange": "%s",
                "priorityClass": "%s",
                "isKeyAction": %b
            }
            """,
            activity.getId(),
            activity.getUserId(),
            activity.getUsername() != null ? activity.getUsername() : "",
            activity.getDisplayName(),
            activity.getActionType(),
            activity.getActionIcon(),
            activity.getActionDescription() != null ? activity.getActionDescription() : "",
            activity.getTimestamp(),
            activity.getFormattedTimestamp(),
            activity.getOrderId() != null ? activity.getOrderId() : "",
            activity.getOrderDisplayInfo(),
            activity.getStateChangeDisplay(),
            activity.getPriorityClass(),
            activity.getIsKeyAction()
        );
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
                (BigDecimal) row[2]
            ))
            .collect(Collectors.toList());
    }
    
    private List<StuckUser> convertStuckUsers(List<Object[]> stuckUsers) {
        return stuckUsers.stream()
            .map(row -> new StuckUser(
                (Long) row[0],
                (String) row[1],
                (LocalDateTime) row[2]
            ))
            .collect(Collectors.toList());
    }
    
    private BigDecimal calculateAverageOrderAmount(List<UserActivityLogEntity> completedPayments) {
        if (completedPayments.isEmpty()) return BigDecimal.ZERO;
        
        List<BigDecimal> amounts = completedPayments.stream()
            .map(UserActivityLogEntity::getOrderAmount)
            .filter(Objects::nonNull)
            .toList();
        
        if (amounts.isEmpty()) return BigDecimal.ZERO;
        
        BigDecimal sum = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(amounts.size()), 2, BigDecimal.ROUND_HALF_UP);
    }
    
    private String findMostPopularPackage(List<UserActivityLogEntity> completedPayments) {
        Map<Integer, Long> packageCounts = completedPayments.stream()
            .filter(a -> a.getStarCount() != null)
            .collect(Collectors.groupingBy(
                UserActivityLogEntity::getStarCount,
                Collectors.counting()
            ));
        
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
}
