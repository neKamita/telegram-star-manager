package shit.back.service.activity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import shit.back.entity.UserActivityLogEntity;
import shit.back.entity.UserActivityLogEntity.LogCategory;
import shit.back.repository.UserActivityLogJpaRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

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

/**
 * Сервис для работы с SSE соединениями и real-time обновлениями
 * РЕФАКТОРИНГ: Выделен из UserActivityLogService для соблюдения SRP
 * 
 * Отвечает только за:
 * - Управление SSE соединениями
 * - Broadcast активности клиентам
 * - Поддержку категорийной фильтрации
 * - Кэширование последних активностей
 */
@Slf4j
@Service
public class UserActivitySSEService {

    @Autowired
    private UserActivityLogJpaRepository activityLogRepository;

    // SSE connections для real-time обновлений с поддержкой категорий
    private final Map<String, CategorySseConnection> sseConnections = new ConcurrentHashMap<>();
    private final List<UserActivityLogEntity> recentActivities = new CopyOnWriteArrayList<>();

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
    public void broadcastActivity(UserActivityLogEntity activity) {
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

    /**
     * Добавить активность в список последних
     */
    public void addToRecentActivities(UserActivityLogEntity activity) {
        recentActivities.add(0, activity);

        // Оставляем только последние 100 активностей в памяти
        if (recentActivities.size() > 100) {
            recentActivities.subList(100, recentActivities.size()).clear();
        }
    }

    /**
     * Получить последние активности для live feed
     */
    public List<UserActivityLogEntity> getRecentActivities(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return activityLogRepository.findRecentKeyActivities(since);
    }

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

    /**
     * Конвертация активности в JSON для отправки через SSE
     */
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
}