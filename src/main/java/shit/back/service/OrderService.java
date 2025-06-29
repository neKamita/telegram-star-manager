package shit.back.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shit.back.dto.order.*;
import shit.back.entity.OrderEntity;
import shit.back.model.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Фасад для управления заказами
 * Делегирует операции специализированным сервисам
 * Следует принципам SOLID и Clean Architecture
 */
@Slf4j
@Service
@Transactional
public class OrderService {

    @Autowired
    private OrderQueryService orderQueryService;

    @Autowired
    private OrderUpdateService orderUpdateService;

    @Autowired
    private OrderValidationService orderValidationService;

    // ============= ФАСАДНЫЕ МЕТОДЫ (ДЕЛЕГИРОВАНИЕ) =============

    /**
     * Создание заказа
     */
    public OrderEntity createOrder(Order order) {
        return orderUpdateService.createOrder(order);
    }

    /**
     * Создание заказа с проверкой баланса
     */
    public OrderEntity createOrderWithBalanceCheck(Order order) {
        return orderUpdateService.createOrderWithBalanceCheck(order);
    }

    /**
     * Обновление статуса заказа
     */
    public Optional<OrderEntity> updateOrderStatus(String orderId, OrderEntity.OrderStatus status) {
        return orderUpdateService.updateOrderStatus(orderId, status);
    }

    /**
     * Обновление заметок заказа
     */
    public Optional<OrderEntity> updateOrderNotes(String orderId, String notes, String updatedBy) {
        return orderUpdateService.updateOrderNotes(orderId, notes, updatedBy);
    }

    /**
     * Массовое обновление статуса
     */
    public BatchUpdateResult batchUpdateStatus(List<String> orderIds, OrderEntity.OrderStatus newStatus,
            String updatedBy) {
        return orderUpdateService.batchUpdateStatus(orderIds, newStatus, updatedBy);
    }

    /**
     * Обработка оплаты балансом
     */
    public OrderEntity processBalancePayment(String orderId, Long userId) {
        return orderUpdateService.processBalancePayment(orderId, userId);
    }

    /**
     * Обработка смешанной оплаты
     */
    public OrderEntity processMixedPayment(String orderId, Long userId, BigDecimal balanceAmount,
            BigDecimal externalAmount) {
        return orderUpdateService.processMixedPayment(orderId, userId, balanceAmount, externalAmount);
    }

    /**
     * Отмена заказа с возвратом
     */
    public OrderEntity cancelOrderWithBalanceRefund(String orderId, Long userId, String reason) {
        return orderUpdateService.cancelOrderWithBalanceRefund(orderId, userId, reason);
    }

    // ============= МЕТОДЫ ЗАПРОСОВ (ДЕЛЕГИРОВАНИЕ К OrderQueryService)
    // =============

    @Transactional(readOnly = true)
    public Optional<OrderEntity> getOrderById(String orderId) {
        return orderQueryService.getOrderById(orderId);
    }

    @Transactional(readOnly = true)
    public List<OrderEntity> getOrdersByUserId(Long userId) {
        return orderQueryService.getOrdersByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<OrderEntity> getRecentOrders(int days) {
        return orderQueryService.getRecentOrders(days);
    }

    @Transactional(readOnly = true)
    public List<OrderEntity> getOrdersByStatus(OrderEntity.OrderStatus status) {
        return orderQueryService.getOrdersByStatus(status);
    }

    @Transactional(readOnly = true)
    public Page<OrderEntity> getOrders(Pageable pageable) {
        return orderQueryService.getOrders(pageable);
    }

    @Transactional(readOnly = true)
    public Page<OrderEntity> searchOrders(String searchTerm, Pageable pageable) {
        return orderQueryService.searchOrders(searchTerm, pageable);
    }

    @Transactional(readOnly = true)
    public List<OrderEntity> getOrdersInDateRange(LocalDateTime start, LocalDateTime end) {
        return orderQueryService.getOrdersInDateRange(start, end);
    }

    @Transactional(readOnly = true)
    public List<OrderEntity> getActiveOrdersByUserId(Long userId) {
        return orderQueryService.getActiveOrdersByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<OrderEntity> getTodaysOrders() {
        return orderQueryService.getTodaysOrders();
    }

    @Transactional(readOnly = true)
    public Page<OrderEntity> searchOrdersWithFilters(String searchText, OrderEntity.OrderStatus status,
            LocalDateTime fromDate, LocalDateTime toDate, Pageable pageable) {
        return orderQueryService.searchOrdersWithFilters(searchText, status, fromDate, toDate, pageable);
    }

    @Transactional(readOnly = true)
    public OrderStatistics getOrderStatisticsOptimized() {
        return orderQueryService.getOrderStatisticsOptimized();
    }

    @Transactional(readOnly = true)
    public List<CustomerStats> getTopCustomers(int limit) {
        return orderQueryService.getTopCustomers(limit);
    }

    @Transactional(readOnly = true)
    public List<DailyStats> getDailyStatistics(int days) {
        return orderQueryService.getDailyStatistics(days);
    }

    @Transactional(readOnly = true)
    public List<PackageStats> getPackageStatistics() {
        return orderQueryService.getPackageStatistics();
    }

    @Transactional(readOnly = true)
    public OrderMetrics getOrderMetrics(LocalDateTime from, LocalDateTime to) {
        return orderQueryService.getOrderMetrics(from, to);
    }

    @Transactional(readOnly = true)
    public Map<OrderEntity.OrderStatus, Long> getOrderStatusStatistics() {
        return orderQueryService.getOrderStatusStatistics();
    }

    // Простые счетчики
    @Transactional(readOnly = true)
    public long getTotalOrdersCount() {
        return orderQueryService.getTotalOrdersCount();
    }

    @Transactional(readOnly = true)
    public long getCompletedOrdersCount() {
        return orderQueryService.getCompletedOrdersCount();
    }

    @Transactional(readOnly = true)
    public long getPendingOrdersCount() {
        return orderQueryService.getPendingOrdersCount();
    }

    @Transactional(readOnly = true)
    public long getFailedOrdersCount() {
        return orderQueryService.getFailedOrdersCount();
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalRevenue() {
        return orderQueryService.getTotalRevenue();
    }

    @Transactional(readOnly = true)
    public BigDecimal getTodayRevenue() {
        return orderQueryService.getTodayRevenue();
    }

    @Transactional(readOnly = true)
    public BigDecimal getMonthRevenue() {
        return orderQueryService.getMonthRevenue();
    }

    @Transactional(readOnly = true)
    public BigDecimal getRevenueByDateRange(LocalDateTime start, LocalDateTime end) {
        return orderQueryService.getRevenueByDateRange(start, end);
    }

    @Transactional(readOnly = true)
    public BigDecimal getAverageOrderValue() {
        return orderQueryService.getAverageOrderValue();
    }

    @Transactional(readOnly = true)
    public Double getOrderConversionRate() {
        return orderQueryService.getOrderConversionRate();
    }

    // ============= МЕТОДЫ ВАЛИДАЦИИ (ДЕЛЕГИРОВАНИЕ К OrderValidationService)
    // =============

    public boolean validateStatusTransition(OrderEntity.OrderStatus current, OrderEntity.OrderStatus target) {
        return orderValidationService.validateStatusTransition(current, target);
    }

    public List<OrderEntity.OrderStatus> getValidNextStatuses(OrderEntity.OrderStatus current) {
        return orderValidationService.getValidNextStatuses(current);
    }

    // ============= LEGACY МЕТОДЫ ДЛЯ ОБРАТНОЙ СОВМЕСТИМОСТИ =============

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
}
