package shit.back.service;

import org.springframework.stereotype.Service;
import shit.back.model.UserSession;
import shit.back.model.Order;
import shit.back.model.StarPackage;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;

@Service
public class UserSessionService {
    
    private final Map<Long, UserSession> userSessions = new ConcurrentHashMap<>();
    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    
    public UserSession getOrCreateSession(Long userId, String username, String firstName, String lastName) {
        UserSession session = userSessions.get(userId);
        if (session == null) {
            session = new UserSession(userId, username, firstName, lastName);
            userSessions.put(userId, session);
        } else {
            session.updateActivity();
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
        }
    }
    
    public void setSelectedPackage(Long userId, StarPackage starPackage) {
        UserSession session = userSessions.get(userId);
        if (session != null) {
            session.setSelectedPackage(starPackage);
            session.setState(UserSession.SessionState.CONFIRMING_ORDER);
            session.updateActivity();
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
