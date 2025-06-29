package shit.back.controller.admin.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import shit.back.web.controller.admin.AdminBaseController;
import shit.back.web.controller.admin.AdminDashboardOperations;
import shit.back.entity.OrderEntity;
import shit.back.entity.UserActivityLogEntity.ActionType;
import shit.back.service.OrderService;
import shit.back.dto.order.OrderStatistics;
import shit.back.service.UserActivityLogService;
import shit.back.service.admin.shared.AdminAuthenticationService;
import shit.back.service.admin.shared.AdminValidationService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.*;

/**
 * REST API контроллер для управления заказами
 * Содержит только JSON API endpoints для заказов
 * Следует принципам SOLID и чистой архитектуры
 */
@Slf4j
@RestController
@RequestMapping("/admin/api/orders")
@Validated
public class AdminOrdersApiController extends AdminBaseController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserActivityLogService activityLogService;

    @Autowired
    private AdminValidationService adminValidationService;

    /**
     * Получение заказов с фильтрами (JSON API)
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletRequest request) {

        try {
            log.debug("API orders request - page: {}, size: {}, status: {}", page, size, status);

            // Аутентификация и rate limiting через унифицированный сервис
            if (!validateApiAuthentication(request)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(createErrorResponse("Unauthorized access", null));
            }

            // Валидация параметров
            Map<String, Object> validationResult = adminValidationService.validatePaginationParams(page, size);
            if (!(Boolean) validationResult.get("valid")) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("Invalid parameters", null));
            }

            page = (Integer) validationResult.get("page");
            size = (Integer) validationResult.get("size");

            // Создание параметров сортировки
            Sort sort = adminValidationService.createValidSort(sortBy, sortDir, getValidSortFields());
            Pageable pageable = PageRequest.of(page, size, sort);

            // Получение данных
            Page<OrderEntity> orders = getFilteredOrders(status, search, startDate, endDate, pageable);
            OrderStatistics orderStats = orderService.getOrderStatisticsOptimized();

            // Формирование ответа
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("orders", orders.getContent());
            response.put("pagination", createPaginationInfo(orders));
            response.put("statistics", orderStats);
            response.put("filters", createFiltersInfo(status, search, startDate, endDate, sortBy, sortDir));
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error in orders API", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Internal server error", e));
        }
    }

    /**
     * Получение конкретного заказа по ID
     */
    @GetMapping(value = "/{orderId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getOrder(
            @PathVariable @NotBlank String orderId,
            HttpServletRequest request) {

        try {
            log.debug("Getting order by ID: {}", orderId);

            // Аутентификация
            if (!validateApiAuthentication(request)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(createErrorResponse("Unauthorized access", null));
            }

            // Валидация orderId
            if (!adminValidationService.isValidOrderId(orderId)) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("Invalid order ID format", null));
            }

            // Получение заказа
            Optional<OrderEntity> orderOpt = orderService.getOrderById(orderId);
            if (orderOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            OrderEntity order = orderOpt.get();
            List<shit.back.entity.UserActivityLogEntity> orderActivities = activityLogService
                    .getOrderActivities(orderId);

            Map<String, Object> response = Map.of(
                    "success", true,
                    "order", order,
                    "activities", orderActivities,
                    "availableStatuses", Arrays.asList(OrderEntity.OrderStatus.values()),
                    "timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting order by ID: {}", orderId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to get order", e));
        }
    }

    /**
     * Изменение статуса заказа
     */
    @PostMapping("/{orderId}/status")
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable @NotBlank String orderId,
            @RequestBody @Valid StatusUpdateRequest request,
            HttpServletRequest httpRequest) {

        try {
            log.info("Updating order {} status to {}", orderId, request.getStatus());

            // Аутентификация
            if (!validateApiAuthentication(httpRequest)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(createErrorResponse("Unauthorized", null));
            }

            // Валидация
            if (!adminValidationService.isValidOrderId(orderId)) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("Invalid order ID format", null));
            }

            // Обновление статуса
            Optional<OrderEntity> updatedOrder = orderService.updateOrderStatus(
                    orderId, request.getStatus());

            if (updatedOrder.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            // Логирование изменения
            activityLogService.logApplicationActivity(
                    null, "ADMIN", null, null,
                    ActionType.STATE_CHANGED,
                    String.format("Изменен статус заказа %s на %s. Причина: %s",
                            orderId, request.getStatus(), request.getReason()));

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Order status updated successfully",
                    "order", updatedOrder.get(),
                    "timestamp", LocalDateTime.now()));

        } catch (Exception e) {
            log.error("Error updating order status for orderId: {}", orderId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to update order status", e));
        }
    }

    /**
     * Массовое изменение статуса заказов
     */
    @PostMapping("/batch/status")
    public ResponseEntity<?> batchUpdateStatus(
            @RequestBody @Valid BatchStatusUpdateRequest request,
            HttpServletRequest httpRequest) {

        try {
            log.info("Batch updating status for {} orders to {}",
                    request.getOrderIds().size(), request.getStatus());

            // Аутентификация
            if (!validateApiAuthentication(httpRequest)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(createErrorResponse("Unauthorized", null));
            }

            // Валидация количества заказов
            if (request.getOrderIds().size() > 50) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("Too many orders (max 50)", null));
            }

            // Обновление заказов
            List<String> updatedOrders = new ArrayList<>();
            List<String> failedOrders = new ArrayList<>();

            for (String orderId : request.getOrderIds()) {
                try {
                    if (adminValidationService.isValidOrderId(orderId)) {
                        Optional<OrderEntity> updated = orderService.updateOrderStatus(
                                orderId, request.getStatus());
                        if (updated.isPresent()) {
                            updatedOrders.add(orderId);
                        } else {
                            failedOrders.add(orderId + " (not found)");
                        }
                    } else {
                        failedOrders.add(orderId + " (invalid format)");
                    }
                } catch (Exception e) {
                    failedOrders.add(orderId + " (error: " + e.getMessage() + ")");
                }
            }

            // Логирование
            activityLogService.logApplicationActivity(
                    null, "ADMIN", null, null,
                    ActionType.STATE_CHANGED,
                    String.format("Массовое изменение статуса: %d обновлено, %d ошибок. Новый статус: %s",
                            updatedOrders.size(), failedOrders.size(), request.getStatus()));

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", String.format("Batch update completed: %d updated, %d failed",
                            updatedOrders.size(), failedOrders.size()),
                    "updatedOrders", updatedOrders,
                    "failedOrders", failedOrders,
                    "timestamp", LocalDateTime.now()));

        } catch (Exception e) {
            log.error("Error in batch status update", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Batch update failed", e));
        }
    }

    // Вспомогательные методы

    private Set<String> getValidSortFields() {
        return Set.of("createdAt", "updatedAt", "status", "finalAmount",
                "username", "orderId", "completedAt");
    }

    private Page<OrderEntity> getFilteredOrders(String status, String search,
            String startDate, String endDate, Pageable pageable) {

        if (hasFilters(status, search, startDate, endDate)) {
            return orderService.searchOrders(buildSearchTerm(status, search, startDate, endDate), pageable);
        } else {
            return orderService.getOrders(pageable);
        }
    }

    private boolean hasFilters(String status, String search, String startDate, String endDate) {
        return (status != null && !status.isEmpty()) ||
                (search != null && !search.isEmpty()) ||
                (startDate != null && !startDate.isEmpty()) ||
                (endDate != null && !endDate.isEmpty());
    }

    private String buildSearchTerm(String status, String search, String startDate, String endDate) {
        List<String> terms = new ArrayList<>();

        if (status != null && !status.isEmpty()) {
            terms.add("status:" + status);
        }
        if (search != null && !search.isEmpty()) {
            terms.add(search);
        }

        return String.join(" ", terms);
    }

    private Map<String, Object> createPaginationInfo(Page<OrderEntity> orders) {
        return Map.of(
                "currentPage", orders.getNumber(),
                "totalPages", orders.getTotalPages(),
                "totalElements", orders.getTotalElements(),
                "pageSize", orders.getSize(),
                "hasNext", orders.hasNext(),
                "hasPrevious", orders.hasPrevious());
    }

    private Map<String, Object> createFiltersInfo(String status, String search,
            String startDate, String endDate, String sortBy, String sortDir) {
        return Map.of(
                "status", status,
                "search", search,
                "startDate", startDate,
                "endDate", endDate,
                "sortBy", sortBy,
                "sortDir", sortDir);
    }

    // DTO классы

    /**
     * Запрос на обновление статуса заказа
     */
    @lombok.Data
    public static class StatusUpdateRequest {
        @NotNull(message = "Status is required")
        private OrderEntity.OrderStatus status;

        @Size(max = 500, message = "Reason must not exceed 500 characters")
        private String reason;
    }

    /**
     * Запрос на массовое обновление статуса
     */
    @lombok.Data
    public static class BatchStatusUpdateRequest {
        @NotNull(message = "Order IDs are required")
        @Size(min = 1, max = 50, message = "Must specify 1-50 order IDs")
        private List<@NotBlank String> orderIds;

        @NotNull(message = "Status is required")
        private OrderEntity.OrderStatus status;

        @Size(max = 500, message = "Reason must not exceed 500 characters")
        private String reason;
    }
}