package shit.back.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shit.back.dto.order.*;
import shit.back.entity.OrderEntity;
import shit.back.repository.OrderJpaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Сервис для запросов и чтения данных заказов
 * Следует принципу Single Responsibility (SRP)
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class OrderQueryService {

    @Autowired
    private OrderJpaRepository orderRepository;

    /**
     * Получение заказа по ID
     */
    public Optional<OrderEntity> getOrderById(String orderId) {
        return orderRepository.findById(orderId);
    }

    /**
     * Получение заказов по пользователю
     */
    public List<OrderEntity> getOrdersByUserId(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Получение недавних заказов
     */
    public List<OrderEntity> getRecentOrders(int days) {
        LocalDateTime fromDate = LocalDateTime.now().minusDays(days);
        return orderRepository.findRecentOrders(fromDate);
    }

    /**
     * Получение заказов по статусу
     */
    public List<OrderEntity> getOrdersByStatus(OrderEntity.OrderStatus status) {
        return orderRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    /**
     * Получение заказов с пагинацией
     */
    public Page<OrderEntity> getOrders(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }

    /**
     * Поиск заказов
     */
    public Page<OrderEntity> searchOrders(String searchTerm, Pageable pageable) {
        return orderRepository.searchOrders(searchTerm, pageable);
    }

    /**
     * Получение заказов в диапазоне дат
     */
    public List<OrderEntity> getOrdersInDateRange(LocalDateTime start, LocalDateTime end) {
        return orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);
    }

    /**
     * Получение активных заказов пользователя
     */
    public List<OrderEntity> getActiveOrdersByUserId(Long userId) {
        return orderRepository.findActiveOrdersByUserId(userId);
    }

    /**
     * Получение заказов за сегодня
     */
    public List<OrderEntity> getTodaysOrders() {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        return orderRepository.getTodaysOrders(startOfDay, endOfDay);
    }

    /**
     * Расширенный поиск заказов с фильтрами
     */
    public Page<OrderEntity> searchOrdersWithFilters(String searchText, OrderEntity.OrderStatus status,
            LocalDateTime fromDate, LocalDateTime toDate, Pageable pageable) {
        log.debug("Advanced search: text={}, status={}, from={}, to={}", searchText, status, fromDate, toDate);

        // Если нет фильтров, возвращаем все заказы
        if (isEmptySearchCriteria(searchText, status, fromDate, toDate)) {
            return orderRepository.findAll(pageable);
        }

        // Если есть только текстовый поиск
        if (status == null && fromDate == null && toDate == null && searchText != null
                && !searchText.trim().isEmpty()) {
            return orderRepository.searchOrders(searchText.trim(), pageable);
        }

        // Для сложных фильтров используем фильтрацию через stream
        List<OrderEntity> allOrders;

        if (fromDate != null && toDate != null) {
            allOrders = orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(fromDate, toDate);
        } else if (fromDate != null) {
            allOrders = orderRepository.findRecentOrders(fromDate);
        } else {
            allOrders = orderRepository.findAll();
        }

        // Применяем фильтры
        List<OrderEntity> filteredOrders = allOrders.stream()
                .filter(order -> status == null || order.getStatus() == status)
                .filter(order -> searchText == null || searchText.trim().isEmpty() ||
                        matchesSearchText(order, searchText.trim()))
                .toList();

        // Применяем пагинацию
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filteredOrders.size());
        List<OrderEntity> pageContent = start < filteredOrders.size() ? filteredOrders.subList(start, end) : List.of();

        return new org.springframework.data.domain.PageImpl<>(pageContent, pageable, filteredOrders.size());
    }

    /**
     * Получение статистики заказов (оптимизированная версия)
     */
    public OrderStatistics getOrderStatisticsOptimized() {
        log.debug("Getting optimized order statistics with single SQL query");

        try {
            List<Object[]> result = orderRepository.getOrderStatisticsOptimized();

            if (result.isEmpty() || result.get(0) == null) {
                log.warn("No order statistics data returned, using defaults");
                return createEmptyOrderStatistics();
            }

            Object[] row = result.get(0);

            // Извлекаем все значения из одного запроса
            Long totalOrders = ((Number) row[0]).longValue();
            Long completedOrders = ((Number) row[1]).longValue();
            Long pendingOrders = ((Number) row[2]).longValue();
            Long failedOrders = ((Number) row[3]).longValue();
            BigDecimal totalRevenue = (BigDecimal) row[4];
            BigDecimal todayRevenue = (BigDecimal) row[5];
            BigDecimal monthRevenue = (BigDecimal) row[6];
            BigDecimal averageOrderValue = (BigDecimal) row[7];

            // Вычисляем конверсию
            Double conversionRate = totalOrders > 0 ? (completedOrders * 100.0) / totalOrders : 0.0;

            return OrderStatistics.builder()
                    .totalOrders(totalOrders != null ? totalOrders : 0L)
                    .completedOrders(completedOrders != null ? completedOrders : 0L)
                    .pendingOrders(pendingOrders != null ? pendingOrders : 0L)
                    .failedOrders(failedOrders != null ? failedOrders : 0L)
                    .totalRevenue(totalRevenue != null ? totalRevenue : BigDecimal.ZERO)
                    .todayRevenue(todayRevenue != null ? todayRevenue : BigDecimal.ZERO)
                    .monthRevenue(monthRevenue != null ? monthRevenue : BigDecimal.ZERO)
                    .averageOrderValue(averageOrderValue != null ? averageOrderValue : BigDecimal.ZERO)
                    .conversionRate(conversionRate)
                    .build();

        } catch (Exception e) {
            log.error("Error getting optimized order statistics: {}", e.getMessage(), e);
            return createEmptyOrderStatistics();
        }
    }

    /**
     * Получение топ клиентов
     */
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
     * Получение дневной статистики
     */
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
     * Получение статистики по пакетам
     */
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

    /**
     * Получение метрик заказов за период
     */
    public OrderMetrics getOrderMetrics(LocalDateTime from, LocalDateTime to) {
        log.debug("Getting order metrics from {} to {}", from, to);

        if (from == null) {
            from = LocalDateTime.now().minusDays(30);
        }
        if (to == null) {
            to = LocalDateTime.now();
        }

        List<OrderEntity> ordersInPeriod = orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to);

        long totalOrders = ordersInPeriod.size();
        long completedOrders = ordersInPeriod.stream()
                .mapToLong(order -> order.getStatus() == OrderEntity.OrderStatus.COMPLETED ? 1 : 0)
                .sum();
        long failedOrders = ordersInPeriod.stream()
                .mapToLong(order -> order.getStatus() == OrderEntity.OrderStatus.FAILED ? 1 : 0)
                .sum();
        long pendingOrders = ordersInPeriod.stream()
                .mapToLong(order -> order.isPaymentPending() ? 1 : 0)
                .sum();

        BigDecimal totalRevenue = ordersInPeriod.stream()
                .filter(order -> order.getStatus() == OrderEntity.OrderStatus.COMPLETED)
                .map(OrderEntity::getFinalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgOrderValue = totalOrders > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        double conversionRate = totalOrders > 0 ? (completedOrders * 100.0) / totalOrders : 0.0;

        return OrderMetrics.builder()
                .periodStart(from)
                .periodEnd(to)
                .totalOrders(totalOrders)
                .completedOrders(completedOrders)
                .pendingOrders(pendingOrders)
                .failedOrders(failedOrders)
                .totalRevenue(totalRevenue)
                .averageOrderValue(avgOrderValue)
                .conversionRate(conversionRate)
                .build();
    }

    /**
     * Получение статистики по статусам заказов
     */
    public Map<OrderEntity.OrderStatus, Long> getOrderStatusStatistics() {
        log.debug("Getting order status statistics");

        Map<OrderEntity.OrderStatus, Long> statistics = new HashMap<>();

        for (OrderEntity.OrderStatus status : OrderEntity.OrderStatus.values()) {
            Long count = orderRepository.countByStatus(status);
            statistics.put(status, count != null ? count : 0L);
        }

        return statistics;
    }

    // Простые счетчики
    public long getTotalOrdersCount() {
        return orderRepository.count();
    }

    public long getCompletedOrdersCount() {
        return orderRepository.countByStatus(OrderEntity.OrderStatus.COMPLETED);
    }

    public long getPendingOrdersCount() {
        return orderRepository.countByStatus(OrderEntity.OrderStatus.CREATED) +
                orderRepository.countByStatus(OrderEntity.OrderStatus.AWAITING_PAYMENT);
    }

    public long getFailedOrdersCount() {
        return orderRepository.countByStatus(OrderEntity.OrderStatus.FAILED);
    }

    public BigDecimal getTotalRevenue() {
        BigDecimal revenue = orderRepository.getTotalRevenue();
        return revenue != null ? revenue : BigDecimal.ZERO;
    }

    public BigDecimal getTodayRevenue() {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        BigDecimal revenue = orderRepository.getRevenueSince(startOfDay);
        return revenue != null ? revenue : BigDecimal.ZERO;
    }

    public BigDecimal getMonthRevenue() {
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        BigDecimal revenue = orderRepository.getRevenueSince(startOfMonth);
        return revenue != null ? revenue : BigDecimal.ZERO;
    }

    public BigDecimal getRevenueByDateRange(LocalDateTime start, LocalDateTime end) {
        List<OrderEntity> orders = getOrdersInDateRange(start, end);
        return orders.stream()
                .filter(order -> order.getStatus() == OrderEntity.OrderStatus.COMPLETED)
                .map(OrderEntity::getFinalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getAverageOrderValue() {
        BigDecimal avg = orderRepository.getAverageOrderValue();
        return avg != null ? avg : BigDecimal.ZERO;
    }

    public Double getOrderConversionRate() {
        Double rate = orderRepository.getOrderConversionRate();
        return rate != null ? rate : 0.0;
    }

    // Вспомогательные методы
    private OrderStatistics createEmptyOrderStatistics() {
        return OrderStatistics.builder()
                .totalOrders(0L)
                .completedOrders(0L)
                .pendingOrders(0L)
                .failedOrders(0L)
                .totalRevenue(BigDecimal.ZERO)
                .todayRevenue(BigDecimal.ZERO)
                .monthRevenue(BigDecimal.ZERO)
                .averageOrderValue(BigDecimal.ZERO)
                .conversionRate(0.0)
                .build();
    }

    private boolean isEmptySearchCriteria(String searchText, OrderEntity.OrderStatus status,
            LocalDateTime fromDate, LocalDateTime toDate) {
        return (searchText == null || searchText.trim().isEmpty()) &&
                status == null &&
                fromDate == null &&
                toDate == null;
    }

    private boolean matchesSearchText(OrderEntity order, String searchText) {
        String lowerSearchText = searchText.toLowerCase();
        return (order.getOrderId() != null && order.getOrderId().toLowerCase().contains(lowerSearchText)) ||
                (order.getUsername() != null && order.getUsername().toLowerCase().contains(lowerSearchText)) ||
                (order.getStarPackageName() != null
                        && order.getStarPackageName().toLowerCase().contains(lowerSearchText))
                ||
                (order.getNotes() != null && order.getNotes().toLowerCase().contains(lowerSearchText));
    }
}