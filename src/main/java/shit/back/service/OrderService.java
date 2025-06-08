package shit.back.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shit.back.entity.OrderEntity;
import shit.back.model.Order;
import shit.back.repository.OrderJpaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing orders and order-related operations
 */
@Slf4j
@Service
@Transactional
public class OrderService {
    
    @Autowired
    private OrderJpaRepository orderRepository;
    
    /**
     * Create a new order
     */
    public OrderEntity createOrder(Order order) {
        log.info("Creating new order for user {}, amount: {}", order.getUserId(), order.getAmount());
        
        OrderEntity entity = new OrderEntity(
                order.getOrderId(),
                order.getUserId(),
                order.getUsername(),
                order.getStarPackage().getPackageId(),
                order.getStarPackage().getStars(),
                order.getStarPackage().getOriginalPrice(),
                order.getStarPackage().getDiscountPercent(),
                order.getAmount()
        );
        entity.setPaymentMethod("TELEGRAM_STARS");
        
        OrderEntity saved = orderRepository.save(entity);
        log.info("Order created with ID: {}", saved.getOrderId());
        return saved;
    }
    
    /**
     * Update order status
     */
    public Optional<OrderEntity> updateOrderStatus(String orderId, OrderEntity.OrderStatus status) {
        log.info("Updating order {} status to {}", orderId, status);
        
        Optional<OrderEntity> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isPresent()) {
            OrderEntity order = orderOpt.get();
            order.updateStatus(status);
            
            OrderEntity updated = orderRepository.save(order);
            log.info("Order {} status updated to {}", orderId, status);
            return Optional.of(updated);
        }
        
        log.warn("Order {} not found for status update", orderId);
        return Optional.empty();
    }
    
    /**
     * Get order by ID
     */
    @Transactional(readOnly = true)
    public Optional<OrderEntity> getOrderById(String orderId) {
        return orderRepository.findById(orderId);
    }
    
    /**
     * Get orders by user ID
     */
    @Transactional(readOnly = true)
    public List<OrderEntity> getOrdersByUserId(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
    
    /**
     * Get recent orders
     */
    @Transactional(readOnly = true)
    public List<OrderEntity> getRecentOrders(int days) {
        LocalDateTime fromDate = LocalDateTime.now().minusDays(days);
        return orderRepository.findRecentOrders(fromDate);
    }
    
    /**
     * Get orders by status
     */
    @Transactional(readOnly = true)
    public List<OrderEntity> getOrdersByStatus(OrderEntity.OrderStatus status) {
        return orderRepository.findByStatusOrderByCreatedAtDesc(status);
    }
    
    /**
     * Get paginated orders
     */
    @Transactional(readOnly = true)
    public Page<OrderEntity> getOrders(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }
    
    /**
     * Search orders
     */
    @Transactional(readOnly = true)
    public Page<OrderEntity> searchOrders(String searchTerm, Pageable pageable) {
        return orderRepository.searchOrders(searchTerm, pageable);
    }
    
    /**
     * Get orders in date range
     */
    @Transactional(readOnly = true)
    public List<OrderEntity> getOrdersInDateRange(LocalDateTime start, LocalDateTime end) {
        return orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);
    }
    
    /**
     * Get active orders by user
     */
    @Transactional(readOnly = true)
    public List<OrderEntity> getActiveOrdersByUserId(Long userId) {
        return orderRepository.findActiveOrdersByUserId(userId);
    }
    
    /**
     * Get today's orders
     */
    @Transactional(readOnly = true)
    public List<OrderEntity> getTodaysOrders() {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        return orderRepository.getTodaysOrders(startOfDay, endOfDay);
    }
    
    // Statistics methods
    
    /**
     * Get total orders count
     */
    @Transactional(readOnly = true)
    public long getTotalOrdersCount() {
        return orderRepository.count();
    }
    
    /**
     * Get completed orders count
     */
    @Transactional(readOnly = true)
    public long getCompletedOrdersCount() {
        return orderRepository.countByStatus(OrderEntity.OrderStatus.COMPLETED);
    }
    
    /**
     * Get pending orders count
     */
    @Transactional(readOnly = true)
    public long getPendingOrdersCount() {
        return orderRepository.countByStatus(OrderEntity.OrderStatus.CREATED) +
               orderRepository.countByStatus(OrderEntity.OrderStatus.AWAITING_PAYMENT);
    }
    
    /**
     * Get failed orders count
     */
    @Transactional(readOnly = true)
    public long getFailedOrdersCount() {
        return orderRepository.countByStatus(OrderEntity.OrderStatus.FAILED);
    }
    
    /**
     * Get total revenue
     */
    @Transactional(readOnly = true)
    public BigDecimal getTotalRevenue() {
        BigDecimal revenue = orderRepository.getTotalRevenue();
        return revenue != null ? revenue : BigDecimal.ZERO;
    }
    
    /**
     * Get today's revenue
     */
    @Transactional(readOnly = true)
    public BigDecimal getTodayRevenue() {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        BigDecimal revenue = orderRepository.getRevenueSince(startOfDay);
        return revenue != null ? revenue : BigDecimal.ZERO;
    }
    
    /**
     * Get this month's revenue
     */
    @Transactional(readOnly = true)
    public BigDecimal getMonthRevenue() {
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        BigDecimal revenue = orderRepository.getRevenueSince(startOfMonth);
        return revenue != null ? revenue : BigDecimal.ZERO;
    }
    
    /**
     * Get revenue by date range
     */
    @Transactional(readOnly = true)
    public BigDecimal getRevenueByDateRange(LocalDateTime start, LocalDateTime end) {
        // Используем существующий метод для получения заказов в диапазоне и вычисляем сумму
        List<OrderEntity> orders = getOrdersInDateRange(start, end);
        return orders.stream()
                .filter(order -> order.getStatus() == OrderEntity.OrderStatus.COMPLETED)
                .map(OrderEntity::getFinalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * Get average order value
     */
    @Transactional(readOnly = true)
    public BigDecimal getAverageOrderValue() {
        BigDecimal avg = orderRepository.getAverageOrderValue();
        return avg != null ? avg : BigDecimal.ZERO;
    }
    
    /**
     * Get order conversion rate
     */
    @Transactional(readOnly = true)
    public Double getOrderConversionRate() {
        Double rate = orderRepository.getOrderConversionRate();
        return rate != null ? rate : 0.0;
    }
    
    /**
     * Get orders statistics
     */
    @Transactional(readOnly = true)
    public OrderStatistics getOrderStatistics() {
        return OrderStatistics.builder()
                .totalOrders(getTotalOrdersCount())
                .completedOrders(getCompletedOrdersCount())
                .pendingOrders(getPendingOrdersCount())
                .failedOrders(getFailedOrdersCount())
                .totalRevenue(getTotalRevenue())
                .todayRevenue(getTodayRevenue())
                .monthRevenue(getMonthRevenue())
                .averageOrderValue(getAverageOrderValue())
                .conversionRate(getOrderConversionRate())
                .build();
    }
    
    /**
     * Get top customers
     */
    @Transactional(readOnly = true)
    public List<CustomerStats> getTopCustomers(int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit);
        List<Object[]> results = orderRepository.getTopCustomers(pageRequest);
        
        return results.stream()
                .map(row -> CustomerStats.builder()
                        .userId((Long) row[0])
                        .username((String) row[1])
                        .orderCount(((Number) row[2]).longValue())
                        .totalSpent((BigDecimal) row[3])
                        .build())
                .toList();
    }
    
    /**
     * Get daily statistics for the last N days
     */
    @Transactional(readOnly = true)
    public List<DailyStats> getDailyStatistics(int days) {
        LocalDateTime fromDate = LocalDateTime.now().minusDays(days);
        List<Object[]> results = orderRepository.getDailyStatistics(fromDate);
        
        return results.stream()
                .map(row -> DailyStats.builder()
                        .date((java.sql.Date) row[0])
                        .orderCount(((Number) row[1]).longValue())
                        .revenue((BigDecimal) row[2])
                        .build())
                .toList();
    }
    
    /**
     * Get package statistics
     */
    @Transactional(readOnly = true)
    public List<PackageStats> getPackageStatistics() {
        List<Object[]> results = orderRepository.getPackageStatistics();
        
        return results.stream()
                .map(row -> PackageStats.builder()
                        .packageName((String) row[0])
                        .orderCount(((Number) row[1]).longValue())
                        .totalRevenue((BigDecimal) row[2])
                        .build())
                .toList();
    }
    
    // Data transfer objects
    
    @lombok.Data
    @lombok.Builder
    public static class OrderStatistics {
        private long totalOrders;
        private long completedOrders;
        private long pendingOrders;
        private long failedOrders;
        private BigDecimal totalRevenue;
        private BigDecimal todayRevenue;
        private BigDecimal monthRevenue;
        private BigDecimal averageOrderValue;
        private Double conversionRate;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class CustomerStats {
        private Long userId;
        private String username;
        private Long orderCount;
        private BigDecimal totalSpent;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class DailyStats {
        private java.sql.Date date;
        private Long orderCount;
        private BigDecimal revenue;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class PackageStats {
        private String packageName;
        private Long orderCount;
        private BigDecimal totalRevenue;
    }
}
