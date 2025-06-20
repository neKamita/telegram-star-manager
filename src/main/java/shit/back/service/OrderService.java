package shit.back.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shit.back.entity.BalanceTransactionEntity;
import shit.back.entity.OrderEntity;
import shit.back.entity.UserActivityLogEntity.ActionType;
import shit.back.model.Order;
import shit.back.repository.OrderJpaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import shit.back.dto.order.OrderStatistics;
import shit.back.dto.order.CustomerStats;
import shit.back.dto.order.DailyStats;
import shit.back.dto.order.PackageStats;
import shit.back.dto.order.BatchUpdateResult;
import shit.back.dto.order.OrderMetrics;

/**
 * Service for managing orders and order-related operations
 */
@Slf4j
@Service
@Transactional
public class OrderService {

    @Autowired
    private OrderJpaRepository orderRepository;

    @Autowired
    private UserActivityLogService activityLogService;

    // === ИНТЕГРАЦИЯ С БАЛАНСОМ ===
    @Autowired
    private BalanceService balanceService;


    /**
     * Create a new order with balance check integration
     */
    public OrderEntity createOrder(Order order) {
        log.info("Создание заказа для пользователя {} с проверкой баланса, сумма: {}",
                order.getUserId(), order.getAmount());

        try {
            // Создаем базовый заказ
            OrderEntity entity = new OrderEntity(
                    order.getOrderId(),
                    order.getUserId(),
                    order.getUsername(),
                    order.getStarPackage().getPackageId(),
                    order.getStarPackage().getStars(),
                    order.getStarPackage().getOriginalPrice(),
                    order.getStarPackage().getDiscountPercent(),
                    order.getAmount());
            entity.setPaymentMethod("TELEGRAM_STARS");

            // Проверяем доступность баланса для информирования пользователя
            boolean hasSufficientBalance = balanceService.checkSufficientBalance(
                    order.getUserId(), order.getAmount());

            if (hasSufficientBalance) {
                log.info("У пользователя {} достаточно средств на балансе для заказа {}",
                        order.getUserId(), entity.getOrderId());
            } else {
                log.info("У пользователя {} недостаточно средств на балансе для заказа {}",
                        order.getUserId(), entity.getOrderId());
            }

            OrderEntity saved = orderRepository.save(entity);

            // Логируем создание заказа
            activityLogService.logOrderActivity(
                    saved.getUserId(),
                    saved.getUsername(),
                    null, null,
                    ActionType.ORDER_CREATED,
                    "Создан заказ на " + saved.getStarCount() + "⭐ пакет" +
                            (hasSufficientBalance ? " (баланс достаточен)" : " (требуется пополнение баланса)"),
                    saved.getOrderId(),
                    saved.getFinalAmount(),
                    saved.getStarCount(),
                    saved.getPaymentMethod());

            log.info("Заказ создан с ID: {}, баланс достаточен: {}", saved.getOrderId(), hasSufficientBalance);
            return saved;

        } catch (Exception e) {
            log.error("Ошибка при создании заказа для пользователя {}: {}", order.getUserId(), e.getMessage(), e);
            throw new RuntimeException("Не удалось создать заказ", e);
        }
    }

    /**
     * Update order status with balance integration
     */
    public Optional<OrderEntity> updateOrderStatus(String orderId, OrderEntity.OrderStatus status) {
        log.info("Обновление статуса заказа {} на {} с интеграцией баланса", orderId, status);

        try {
            Optional<OrderEntity> orderOpt = orderRepository.findById(orderId);
            if (orderOpt.isPresent()) {
                OrderEntity order = orderOpt.get();
                OrderEntity.OrderStatus previousStatus = order.getStatus();

                // Специальная обработка для балансовых операций
                if (status == OrderEntity.OrderStatus.COMPLETED && order.isBalanceUsed()) {
                    // Заказ завершен с использованием баланса - никаких дополнительных действий не
                    // требуется
                    log.info("Завершение заказа {} с балансовой оплатой", orderId);
                }

                order.updateStatus(status);
                OrderEntity updated = orderRepository.save(order);

                // Логируем изменение статуса
                ActionType actionType = mapOrderStatusToActionType(status);
                String description = String.format("Статус заказа изменен с %s на %s", previousStatus, status);
                if (order.isBalanceUsed()) {
                    description += " (использован баланс: " + order.getBalanceUsedAmount() + ")";
                }

                activityLogService.logOrderActivity(
                        updated.getUserId(),
                        updated.getUsername(),
                        null, null,
                        actionType,
                        description,
                        updated.getOrderId(),
                        updated.getFinalAmount(),
                        updated.getStarCount(),
                        updated.getPaymentMethod());

                log.info("Статус заказа {} обновлен с {} на {}", orderId, previousStatus, status);
                return Optional.of(updated);
            }

            log.warn("Заказ {} не найден для обновления статуса", orderId);
            return Optional.empty();

        } catch (Exception e) {
            log.error("Ошибка при обновлении статуса заказа {}: {}", orderId, e.getMessage(), e);
            throw new RuntimeException("Не удалось обновить статус заказа", e);
        }
    }

    /**
     * Map order status to appropriate action type for logging
     */
    private ActionType mapOrderStatusToActionType(OrderEntity.OrderStatus status) {
        return switch (status) {
            case AWAITING_PAYMENT -> ActionType.PAYMENT_INITIATED;
            case COMPLETED -> ActionType.PAYMENT_COMPLETED;
            case FAILED -> ActionType.PAYMENT_FAILED;
            case CANCELLED -> ActionType.ORDER_CANCELLED;
            case BALANCE_INSUFFICIENT -> ActionType.PAYMENT_FAILED;
            case PARTIAL_BALANCE_PAYMENT -> ActionType.PAYMENT_INITIATED;
            default -> ActionType.STATE_CHANGED;
        };
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
        // Используем существующий метод для получения заказов в диапазоне и вычисляем
        // сумму
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
     * Get orders statistics (LEGACY - uses multiple queries)
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
     * ОПТИМИЗИРОВАННЫЙ метод получения статистики заказов
     * Объединяет все 9+ отдельных SQL запросов в ОДИН запрос с CASE WHEN агрегацией
     * Значительно улучшает производительность на Orders и Dashboard страницах
     */
    @Transactional(readOnly = true)
    public OrderStatistics getOrderStatisticsOptimized() {
        log.debug("Getting optimized order statistics with single SQL query");

        try {
            // Используем нативный SQL запрос для максимальной производительности
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

            OrderStatistics statistics = OrderStatistics.builder()
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

            log.debug("Optimized order statistics retrieved: {} total orders, {} completed",
                    totalOrders, completedOrders);

            return statistics;

        } catch (Exception e) {
            log.error("Error getting optimized order statistics: {}", e.getMessage(), e);
            return createEmptyOrderStatistics();
        }
    }

    /**
     * Создает пустую статистику заказов для fallback случаев
     */
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

    // ==================== ДОПОЛНИТЕЛЬНЫЕ МЕТОДЫ ДЛЯ АДМИН-ПАНЕЛИ
    // ====================

    /**
     * Обновление заметок заказа
     */
    @Transactional
    public Optional<OrderEntity> updateOrderNotes(String orderId, String notes, String updatedBy) {
        log.info("Updating notes for order {}, updated by: {}", orderId, updatedBy);

        if (orderId == null || orderId.trim().isEmpty()) {
            log.warn("Invalid orderId provided for notes update: {}", orderId);
            return Optional.empty();
        }

        Optional<OrderEntity> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isPresent()) {
            OrderEntity order = orderOpt.get();
            String previousNotes = order.getNotes();
            order.setNotes(notes);

            OrderEntity updated = orderRepository.save(order);

            // Логирование изменения заметок
            String description = String.format("Notes updated by %s. Previous: [%s], New: [%s]",
                    updatedBy,
                    previousNotes != null ? previousNotes.substring(0, Math.min(50, previousNotes.length())) : "empty",
                    notes != null ? notes.substring(0, Math.min(50, notes.length())) : "empty");

            activityLogService.logOrderActivity(
                    updated.getUserId(),
                    updated.getUsername(),
                    null, null,
                    ActionType.STATE_CHANGED,
                    description,
                    updated.getOrderId(),
                    updated.getFinalAmount(),
                    updated.getStarCount(),
                    updated.getPaymentMethod());

            log.info("Notes updated for order {}", orderId);
            return Optional.of(updated);
        }

        log.warn("Order {} not found for notes update", orderId);
        return Optional.empty();
    }

    /**
     * Валидация перехода между статусами заказа
     */
    public boolean validateStatusTransition(OrderEntity.OrderStatus current, OrderEntity.OrderStatus target) {
        if (current == null || target == null) {
            return false;
        }

        if (current == target) {
            return true; // Разрешить "переход" в тот же статус
        }

        return switch (current) {
            case CREATED -> target == OrderEntity.OrderStatus.AWAITING_PAYMENT ||
                    target == OrderEntity.OrderStatus.CANCELLED ||
                    target == OrderEntity.OrderStatus.FAILED ||
                    target == OrderEntity.OrderStatus.BALANCE_INSUFFICIENT;

            case AWAITING_PAYMENT -> target == OrderEntity.OrderStatus.PAYMENT_RECEIVED ||
                    target == OrderEntity.OrderStatus.FAILED ||
                    target == OrderEntity.OrderStatus.CANCELLED ||
                    target == OrderEntity.OrderStatus.BALANCE_INSUFFICIENT ||
                    target == OrderEntity.OrderStatus.PARTIAL_BALANCE_PAYMENT;

            case PAYMENT_RECEIVED -> target == OrderEntity.OrderStatus.PROCESSING ||
                    target == OrderEntity.OrderStatus.COMPLETED ||
                    target == OrderEntity.OrderStatus.REFUNDED;

            case PROCESSING -> target == OrderEntity.OrderStatus.COMPLETED ||
                    target == OrderEntity.OrderStatus.FAILED;

            case COMPLETED -> target == OrderEntity.OrderStatus.REFUNDED;

            case FAILED -> target == OrderEntity.OrderStatus.CREATED ||
                    target == OrderEntity.OrderStatus.AWAITING_PAYMENT;

            case CANCELLED -> target == OrderEntity.OrderStatus.CREATED ||
                    target == OrderEntity.OrderStatus.AWAITING_PAYMENT;

            case REFUNDED -> false; // Из REFUNDED нельзя перейти в другие статусы

            case BALANCE_INSUFFICIENT -> target == OrderEntity.OrderStatus.AWAITING_PAYMENT ||
                    target == OrderEntity.OrderStatus.CANCELLED ||
                    target == OrderEntity.OrderStatus.PARTIAL_BALANCE_PAYMENT;

            case PARTIAL_BALANCE_PAYMENT -> target == OrderEntity.OrderStatus.PAYMENT_RECEIVED ||
                    target == OrderEntity.OrderStatus.COMPLETED ||
                    target == OrderEntity.OrderStatus.CANCELLED;
        };
    }

    /**
     * Получение списка допустимых статусов для перехода
     */
    public List<OrderEntity.OrderStatus> getValidNextStatuses(OrderEntity.OrderStatus current) {
        if (current == null) {
            return List.of();
        }

        return switch (current) {
            case CREATED -> List.of(
                    OrderEntity.OrderStatus.AWAITING_PAYMENT,
                    OrderEntity.OrderStatus.CANCELLED,
                    OrderEntity.OrderStatus.FAILED,
                    OrderEntity.OrderStatus.BALANCE_INSUFFICIENT);

            case AWAITING_PAYMENT -> List.of(
                    OrderEntity.OrderStatus.PAYMENT_RECEIVED,
                    OrderEntity.OrderStatus.FAILED,
                    OrderEntity.OrderStatus.CANCELLED,
                    OrderEntity.OrderStatus.BALANCE_INSUFFICIENT,
                    OrderEntity.OrderStatus.PARTIAL_BALANCE_PAYMENT);

            case PAYMENT_RECEIVED -> List.of(
                    OrderEntity.OrderStatus.PROCESSING,
                    OrderEntity.OrderStatus.COMPLETED,
                    OrderEntity.OrderStatus.REFUNDED);

            case PROCESSING -> List.of(
                    OrderEntity.OrderStatus.COMPLETED,
                    OrderEntity.OrderStatus.FAILED);

            case COMPLETED -> List.of(OrderEntity.OrderStatus.REFUNDED);

            case FAILED -> List.of(
                    OrderEntity.OrderStatus.CREATED,
                    OrderEntity.OrderStatus.AWAITING_PAYMENT);

            case CANCELLED -> List.of(
                    OrderEntity.OrderStatus.CREATED,
                    OrderEntity.OrderStatus.AWAITING_PAYMENT);

            case REFUNDED -> List.of(); // Из REFUNDED нельзя перейти в другие статусы

            case BALANCE_INSUFFICIENT -> List.of(
                    OrderEntity.OrderStatus.AWAITING_PAYMENT,
                    OrderEntity.OrderStatus.CANCELLED,
                    OrderEntity.OrderStatus.PARTIAL_BALANCE_PAYMENT);

            case PARTIAL_BALANCE_PAYMENT -> List.of(
                    OrderEntity.OrderStatus.PAYMENT_RECEIVED,
                    OrderEntity.OrderStatus.COMPLETED,
                    OrderEntity.OrderStatus.CANCELLED);
        };
    }

    /**
     * Массовое изменение статуса заказов
     */
    @Transactional
    public BatchUpdateResult batchUpdateStatus(List<String> orderIds, OrderEntity.OrderStatus newStatus,
            String updatedBy) {
        log.info("Batch updating status for {} orders to {}, updated by: {}", orderIds.size(), newStatus, updatedBy);

        if (orderIds == null || orderIds.isEmpty()) {
            return new BatchUpdateResult(0, 0, List.of(), List.of("No order IDs provided"));
        }

        if (orderIds.size() > 50) {
            return new BatchUpdateResult(0, 0, List.of(), List.of("Too many orders (max 50 allowed)"));
        }

        List<String> updatedOrders = new ArrayList<>();
        List<String> failedOrders = new ArrayList<>();

        for (String orderId : orderIds) {
            try {
                if (orderId == null || orderId.trim().isEmpty()) {
                    failedOrders.add("Empty order ID");
                    continue;
                }

                Optional<OrderEntity> orderOpt = orderRepository.findById(orderId);
                if (orderOpt.isEmpty()) {
                    failedOrders.add(orderId + " (not found)");
                    continue;
                }

                OrderEntity order = orderOpt.get();
                OrderEntity.OrderStatus currentStatus = order.getStatus();

                // Валидация перехода статуса
                if (!validateStatusTransition(currentStatus, newStatus)) {
                    failedOrders.add(orderId + " (invalid transition from " + currentStatus + " to " + newStatus + ")");
                    continue;
                }

                // Обновление статуса
                order.updateStatus(newStatus);
                OrderEntity updated = orderRepository.save(order);

                // Логирование изменения
                activityLogService.logOrderActivity(
                        updated.getUserId(),
                        updated.getUsername(),
                        null, null,
                        mapOrderStatusToActionType(newStatus),
                        String.format("Batch status update by %s from %s to %s", updatedBy, currentStatus, newStatus),
                        updated.getOrderId(),
                        updated.getFinalAmount(),
                        updated.getStarCount(),
                        updated.getPaymentMethod());

                updatedOrders.add(orderId);

            } catch (Exception e) {
                log.error("Error updating order {} in batch operation", orderId, e);
                failedOrders.add(orderId + " (error: " + e.getMessage() + ")");
            }
        }

        log.info("Batch update completed: {} updated, {} failed", updatedOrders.size(), failedOrders.size());
        return new BatchUpdateResult(updatedOrders.size(), failedOrders.size(), updatedOrders, failedOrders);
    }

    /**
     * Расширенный поиск заказов с фильтрами
     */
    @Transactional(readOnly = true)
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

        // Для сложных фильтров используем фильтрацию через stream (можно оптимизировать
        // через custom query)
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
     * Получение метрик заказов за период
     */
    @Transactional(readOnly = true)
    public OrderMetrics getOrderMetrics(LocalDateTime from, LocalDateTime to) {
        log.debug("Getting order metrics from {} to {}", from, to);

        if (from == null) {
            from = LocalDateTime.now().minusDays(30); // По умолчанию последние 30 дней
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
    @Transactional(readOnly = true)
    public Map<OrderEntity.OrderStatus, Long> getOrderStatusStatistics() {
        log.debug("Getting order status statistics");

        Map<OrderEntity.OrderStatus, Long> statistics = new HashMap<>();

        for (OrderEntity.OrderStatus status : OrderEntity.OrderStatus.values()) {
            Long count = orderRepository.countByStatus(status);
            statistics.put(status, count != null ? count : 0L);
        }

        return statistics;
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    /**
     * Проверка на пустые критерии поиска
     */
    private boolean isEmptySearchCriteria(String searchText, OrderEntity.OrderStatus status,
            LocalDateTime fromDate, LocalDateTime toDate) {
        return (searchText == null || searchText.trim().isEmpty()) &&
                status == null &&
                fromDate == null &&
                toDate == null;
    }

    /**
     * Проверка соответствия заказа поисковому тексту
     */
    private boolean matchesSearchText(OrderEntity order, String searchText) {
        String lowerSearchText = searchText.toLowerCase();
        return (order.getOrderId() != null && order.getOrderId().toLowerCase().contains(lowerSearchText)) ||
                (order.getUsername() != null && order.getUsername().toLowerCase().contains(lowerSearchText)) ||
                (order.getStarPackageName() != null
                        && order.getStarPackageName().toLowerCase().contains(lowerSearchText))
                ||
                (order.getNotes() != null && order.getNotes().toLowerCase().contains(lowerSearchText));
    }

    // ==================== МЕТОДЫ ИНТЕГРАЦИИ С БАЛАНСОМ ====================

    /**
     * Создание заказа с проверкой баланса
     *
     * @param order заказ для создания
     * @return созданный заказ с информацией о доступности баланса
     */
    @Transactional
    public OrderEntity createOrderWithBalanceCheck(Order order) {
        log.info("Создание заказа с проверкой баланса для пользователя {}, сумма: {}",
                order.getUserId(), order.getAmount());

        try {
            // Создаем базовый заказ
            OrderEntity entity = createOrder(order);

            // Проверяем доступность средств на балансе
            boolean hasSufficientBalance = balanceService.checkSufficientBalance(
                    order.getUserId(), order.getAmount());

            if (!hasSufficientBalance) {
                // Обновляем статус на недостаточность средств
                entity.updateStatus(OrderEntity.OrderStatus.BALANCE_INSUFFICIENT);
                orderRepository.save(entity);

                log.info("Недостаточно средств на балансе для заказа {}: требуется {}",
                        entity.getOrderId(), order.getAmount());
            }

            return entity;

        } catch (Exception e) {
            log.error("Ошибка при создании заказа с проверкой баланса для пользователя {}: {}",
                    order.getUserId(), e.getMessage(), e);
            throw new RuntimeException("Не удалось создать заказ с проверкой баланса", e);
        }
    }

    /**
     * Обработка оплаты балансом
     *
     * @param orderId ID заказа
     * @param userId  ID пользователя
     * @return обновленный заказ
     */
    @Transactional
    public OrderEntity processBalancePayment(String orderId, Long userId) {
        log.info("Обработка оплаты балансом для заказа {} пользователя {}", orderId, userId);

        try {
            Optional<OrderEntity> orderOpt = getOrderById(orderId);
            if (orderOpt.isEmpty()) {
                throw new RuntimeException("Заказ не найден: " + orderId);
            }

            OrderEntity order = orderOpt.get();

            // Проверяем владельца заказа
            if (!order.getUserId().equals(userId)) {
                throw new RuntimeException("Заказ принадлежит другому пользователю");
            }

            // Обрабатываем платеж через балансовый сервис
            BalanceTransactionEntity transaction = balanceService.processBalancePayment(
                    userId, orderId, order.getFinalAmount());

            // Обновляем информацию о заказе
            order.setBalancePaymentInfo(transaction.getTransactionId(), order.getFinalAmount());
            order.updateStatus(OrderEntity.OrderStatus.PAYMENT_RECEIVED);

            OrderEntity savedOrder = orderRepository.save(order);

            // Логируем активность
            activityLogService.logOrderActivity(
                    userId, order.getUsername(), null, null,
                    ActionType.PAYMENT_COMPLETED,
                    "Оплата заказа балансом: " + order.getFinalAmount(),
                    orderId, order.getFinalAmount(), order.getStarCount(),
                    "BALANCE");

            log.info("Оплата балансом завершена для заказа {}: транзакция {}",
                    orderId, transaction.getTransactionId());

            return savedOrder;

        } catch (Exception e) {
            log.error("Ошибка при обработке оплаты балансом для заказа {}: {}", orderId, e.getMessage(), e);
            throw new RuntimeException("Не удалось обработать оплату балансом", e);
        }
    }

    /**
     * Обработка комбинированной оплаты (баланс + внешняя)
     *
     * @param orderId        ID заказа
     * @param userId         ID пользователя
     * @param balanceAmount  сумма к списанию с баланса
     * @param externalAmount сумма внешней оплаты
     * @return обновленный заказ
     */
    @Transactional
    public OrderEntity processMixedPayment(String orderId, Long userId,
            BigDecimal balanceAmount, BigDecimal externalAmount) {
        log.info("Обработка смешанной оплаты для заказа {}: баланс={}, внешняя={}",
                orderId, balanceAmount, externalAmount);

        try {
            Optional<OrderEntity> orderOpt = getOrderById(orderId);
            if (orderOpt.isEmpty()) {
                throw new RuntimeException("Заказ не найден: " + orderId);
            }

            OrderEntity order = orderOpt.get();

            // Проверяем владельца заказа
            if (!order.getUserId().equals(userId)) {
                throw new RuntimeException("Заказ принадлежит другому пользователю");
            }

            // Проверяем корректность сумм
            BigDecimal totalPayment = balanceAmount.add(externalAmount);
            if (totalPayment.compareTo(order.getFinalAmount()) != 0) {
                throw new RuntimeException("Сумма платежей не соответствует стоимости заказа");
            }

            // Обрабатываем балансовую часть
            BalanceTransactionEntity balanceTransaction = null;
            if (balanceAmount.compareTo(BigDecimal.ZERO) > 0) {
                balanceTransaction = balanceService.processBalancePayment(userId, orderId, balanceAmount);
            }

            // Обновляем информацию о заказе
            order.setBalancePaymentInfo(
                    balanceTransaction != null ? balanceTransaction.getTransactionId() : null,
                    balanceAmount);
            order.setExternalPaymentInfo(externalAmount);
            order.updateStatus(OrderEntity.OrderStatus.PARTIAL_BALANCE_PAYMENT);

            OrderEntity savedOrder = orderRepository.save(order);

            // Логируем активность
            activityLogService.logOrderActivity(
                    userId, order.getUsername(), null, null,
                    ActionType.PAYMENT_INITIATED,
                    String.format("Смешанная оплата заказа: баланс=%s, внешняя=%s",
                            balanceAmount, externalAmount),
                    orderId, order.getFinalAmount(), order.getStarCount(),
                    "MIXED");

            log.info("Смешанная оплата обработана для заказа {}: баланс={}, внешняя={}",
                    orderId, balanceAmount, externalAmount);

            return savedOrder;

        } catch (Exception e) {
            log.error("Ошибка при обработке смешанной оплаты для заказа {}: {}", orderId, e.getMessage(), e);
            throw new RuntimeException("Не удалось обработать смешанную оплату", e);
        }
    }

    /**
     * Отмена заказа с возвратом средств на баланс
     *
     * @param orderId ID заказа
     * @param userId  ID пользователя
     * @param reason  причина отмены
     * @return обновленный заказ
     */
    @Transactional
    public OrderEntity cancelOrderWithBalanceRefund(String orderId, Long userId, String reason) {
        log.info("Отмена заказа {} с возвратом на баланс для пользователя {}: {}",
                orderId, userId, reason);

        try {
            Optional<OrderEntity> orderOpt = getOrderById(orderId);
            if (orderOpt.isEmpty()) {
                throw new RuntimeException("Заказ не найден: " + orderId);
            }

            OrderEntity order = orderOpt.get();

            // Проверяем владельца заказа
            if (!order.getUserId().equals(userId)) {
                throw new RuntimeException("Заказ принадлежит другому пользователю");
            }

            // Проверяем возможность отмены
            if (!order.isCancellable()) {
                throw new RuntimeException("Заказ нельзя отменить в текущем статусе: " + order.getStatus());
            }

            // Если были списания с баланса, возвращаем средства
            if (order.isBalanceUsed() && order.getBalanceUsedAmount().compareTo(BigDecimal.ZERO) > 0) {
                balanceService.refundToBalance(userId, order.getBalanceUsedAmount(), orderId,
                        "Возврат за отмененный заказ: " + reason);

                log.info("Возвращены средства на баланс для заказа {}: {}",
                        orderId, order.getBalanceUsedAmount());
            }

            // Освобождаем зарезервированные средства (если есть)
            balanceService.releaseReservedBalance(userId, orderId);

            // Обновляем статус заказа
            order.updateStatus(OrderEntity.OrderStatus.CANCELLED);
            OrderEntity savedOrder = orderRepository.save(order);

            // Логируем активность
            activityLogService.logOrderActivity(
                    userId, order.getUsername(), null, null,
                    ActionType.ORDER_CANCELLED,
                    "Заказ отменен с возвратом на баланс: " + reason,
                    orderId, order.getFinalAmount(), order.getStarCount(),
                    order.getPaymentMethod());

            log.info("Заказ {} успешно отменен с возвратом средств на баланс", orderId);

            return savedOrder;

        } catch (Exception e) {
            log.error("Ошибка при отмене заказа {} с возвратом на баланс: {}", orderId, e.getMessage(), e);
            throw new RuntimeException("Не удалось отменить заказ с возвратом на баланс", e);
        }
    }

}
