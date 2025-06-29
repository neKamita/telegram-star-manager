package shit.back.service.activity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shit.back.entity.OrderEntity;
import shit.back.entity.UserActivityLogEntity;
import shit.back.entity.UserActivityLogEntity.ActionType;
import shit.back.entity.UserActivityLogEntity.LogCategory;
import shit.back.entity.UserSessionEntity;
import shit.back.repository.UserActivityLogJpaRepository;

import java.math.BigDecimal;

/**
 * Сервис для логирования активности пользователей
 * РЕФАКТОРИНГ: Выделен из UserActivityLogService для соблюдения SRP
 * 
 * Отвечает только за:
 * - Создание записей активности
 * - Сохранение в базу данных
 * - Определение типов действий
 */
@Slf4j
@Service
@Transactional
public class UserActivityLoggingService {

    @Autowired
    private UserActivityLogJpaRepository activityLogRepository;

    @Autowired
    private UserActivitySSEService sseService;

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
            sseService.addToRecentActivities(saved);
            sseService.broadcastActivity(saved);

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
            sseService.addToRecentActivities(saved);

            long dbTime = System.currentTimeMillis();
            log.debug("Order activity saved to DB in {}ms", dbTime - startTime);

            sseService.broadcastActivity(saved);

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
            sseService.addToRecentActivities(saved);

            long dbTime = System.currentTimeMillis();
            log.debug("State change activity saved to DB in {}ms", dbTime - startTime);

            sseService.broadcastActivity(saved);

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
            sseService.addToRecentActivities(saved);
            sseService.broadcastActivity(saved);

        } catch (Exception e) {
            log.error("Error logging order action: {}", e.getMessage(), e);
        }
    }

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
            sseService.addToRecentActivities(saved);

            long dbTime = System.currentTimeMillis();
            log.debug("Telegram bot activity saved to DB in {}ms", dbTime - startTime);

            sseService.broadcastActivity(saved);

            long totalTime = System.currentTimeMillis();
            log.debug("Telegram bot activity broadcast completed in {}ms total", totalTime - startTime);

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
            // Исправляем проблему с NULL user_id - для админских действий используем -1
            Long actualUserId = userId != null ? userId : -1L;
            String actualUsername = username != null ? username : "ADMIN";

            UserActivityLogEntity activity = new UserActivityLogEntity(
                    actualUserId, actualUsername, firstName, lastName, actionType, actionDescription)
                    .withLogCategory(LogCategory.APPLICATION);

            UserActivityLogEntity saved = activityLogRepository.save(activity);
            sseService.addToRecentActivities(saved);
            sseService.broadcastActivity(saved);

            log.debug("Logged application activity: {} for user {}", actionType, actualUsername);
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
            sseService.addToRecentActivities(saved);
            sseService.broadcastActivity(saved);

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
            sseService.addToRecentActivities(saved);
            sseService.broadcastActivity(saved);

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
            sseService.addToRecentActivities(saved);
            sseService.broadcastActivity(saved);

            log.debug("Logged {} activity: {} for user {}", logCategory, actionType, username);
        } catch (Exception e) {
            log.error("Error logging categorized activity: {}", e.getMessage(), e);
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
}