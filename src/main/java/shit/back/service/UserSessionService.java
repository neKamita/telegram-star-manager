package shit.back.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import shit.back.model.UserSession;
import shit.back.model.Order;
import shit.back.model.StarPackage;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;

@Slf4j
@Service
public class UserSessionService {
    
    @Autowired
    private UserSessionEnhancedService enhancedService;
    
    private final Map<Long, UserSession> userSessions = new ConcurrentHashMap<>();
    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    
    public UserSession getOrCreateSession(Long userId, String username, String firstName, String lastName) {
        UserSession session = userSessions.get(userId);
        boolean isNewSession = false;
        
        if (session == null) {
            session = new UserSession(userId, username, firstName, lastName);
            userSessions.put(userId, session);
            isNewSession = true;
            log.info("Created new in-memory session for user {}", userId);
        } else {
            session.updateActivity();
        }
        
        // Синхронизация с PostgreSQL для статистики админ панели
        try {
            enhancedService.createOrUpdateSession(session);
            if (isNewSession) {
                log.info("Synchronized new user {} with PostgreSQL", userId);
            }
        } catch (Exception e) {
            log.warn("Failed to sync session for user {} with PostgreSQL: {}", userId, e.getMessage());
            // Не прерываем работу бота при ошибке синхронизации
        }
        
        return session;
    }
    
    public Optional<UserSession> getSession(Long userId) {
        UserSession session = userSessions.get(userId);
        if (session != null) {
            session.updateActivity();
        }
        return Optional.ofNullable(session);
    }
    
    public void updateSessionState(Long userId, UserSession.SessionState state) {
        UserSession session = userSessions.get(userId);
        if (session != null) {
            session.setState(state);
            session.updateActivity();
            
            // Синхронизация изменения состояния с PostgreSQL
            try {
                enhancedService.createOrUpdateSession(session);
                log.debug("Synchronized state change for user {} to {}", userId, state);
            } catch (Exception e) {
                log.warn("Failed to sync state change for user {} to PostgreSQL: {}", userId, e.getMessage());
            }
        }
    }
    
    public void setSelectedPackage(Long userId, StarPackage starPackage) {
        UserSession session = userSessions.get(userId);
        if (session != null) {
            session.setSelectedPackage(starPackage);
            session.setState(UserSession.SessionState.CONFIRMING_ORDER);
            session.updateActivity();
            
            // Синхронизация выбора пакета с PostgreSQL
            try {
                enhancedService.createOrUpdateSession(session);
                log.debug("Synchronized package selection for user {}", userId);
            } catch (Exception e) {
                log.warn("Failed to sync package selection for user {} to PostgreSQL: {}", userId, e.getMessage());
            }
        }
    }

    public Order createOrder(Long userId) {
        UserSession session = userSessions.get(userId);
        if (session != null && session.getSelectedPackage() != null) {
            Order order = new Order(userId, session.getUsername(), session.getSelectedPackage());
            orders.put(order.getOrderId(), order);
            session.setOrderId(order.getOrderId());
            session.setState(UserSession.SessionState.AWAITING_PAYMENT);
            session.updateActivity();
            
            // Синхронизация создания заказа с PostgreSQL
            try {
                enhancedService.createOrUpdateSession(session);
                log.info("Synchronized order creation for user {}, orderId: {}", userId, order.getOrderId());
            } catch (Exception e) {
                log.warn("Failed to sync order creation for user {} to PostgreSQL: {}", userId, e.getMessage());
            }
            
            return order;
        }
        return null;
    }
    
    public Optional<Order> getOrder(String orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }
    
    public Optional<Order> getUserActiveOrder(Long userId) {
        UserSession session = userSessions.get(userId);
        if (session != null && session.getOrderId() != null) {
            return getOrder(session.getOrderId());
        }
        return Optional.empty();
    }
    
    public void updateOrderStatus(String orderId, Order.OrderStatus status) {
        Order order = orders.get(orderId);
        if (order != null) {
            order.updateStatus(status);
        }
    }
    
    public void clearUserSession(Long userId) {
        UserSession session = userSessions.get(userId);
        if (session != null) {
            session.setState(UserSession.SessionState.IDLE);
            session.setSelectedPackage(null);
            session.setOrderId(null);
            session.updateActivity();
            
            // Синхронизация очистки сессии с PostgreSQL
            try {
                enhancedService.createOrUpdateSession(session);
                log.debug("Synchronized session clear for user {}", userId);
            } catch (Exception e) {
                log.warn("Failed to sync session clear for user {} to PostgreSQL: {}", userId, e.getMessage());
            }
        }
    }
    
    public void cleanupOldSessions() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        userSessions.entrySet().removeIf(entry -> 
            entry.getValue().getLastActivity().isBefore(cutoff));
    }
    
    public int getActiveSessionsCount() {
        return userSessions.size();
    }
    
    public int getTotalOrdersCount() {
        return orders.size();
    }
}
