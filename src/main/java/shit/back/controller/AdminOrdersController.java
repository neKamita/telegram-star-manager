package shit.back.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import shit.back.config.SecurityProperties;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import shit.back.entity.OrderEntity;
import shit.back.entity.UserActivityLogEntity.ActionType;
import shit.back.security.RateLimitService;
import shit.back.security.SecurityValidator;
import shit.back.service.OrderService;
import shit.back.service.UserActivityLogService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Контроллер для управления заказами администратором
 * Включает CSRF защиту, аутентификацию, валидацию и rate limiting
 */
@Slf4j
@Controller
@RequestMapping("/admin/orders")
@Validated
public class AdminOrdersController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserActivityLogService activityLogService;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private SecurityValidator securityValidator;

    @Autowired
    private SecurityProperties securityProperties;

    // ==================== ОСНОВНЫЕ СТРАНИЦЫ ====================

    /**
     * Главная страница со списком заказов
     */
    @GetMapping
    public String ordersListPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Model model,
            HttpServletRequest request) {

        long startTime = System.currentTimeMillis();

        try {
            log.info("Loading orders list page - page: {}, size: {}, search: {}", page, size, search);

            // Проверка аутентификации и авторизации
            if (!isValidAdminRequest(request)) {
                log.warn("Unauthorized access attempt to orders page from IP: {}",
                        request.getRemoteAddr());
                return "redirect:/admin/login";
            }

            // Валидация параметров
            if (page < 0)
                page = 0;
            if (size < 1 || size > 100)
                size = 20;

            // Создание параметров сортировки
            Sort sort = createSortFromParams(sortBy, sortDir);
            Pageable pageable = PageRequest.of(page, size, sort);

            // Получение заказов с фильтрами
            Page<OrderEntity> orders = getFilteredOrders(status, search, startDate, endDate, pageable);

            // Получение статистики заказов
            OrderService.OrderStatistics orderStats = orderService.getOrderStatistics();

            // Статусы для фильтра
            List<OrderEntity.OrderStatus> availableStatuses = Arrays.asList(OrderEntity.OrderStatus.values());

            // Подготовка модели
            model.addAttribute("title", "Управление заказами");
            model.addAttribute("subtitle", "Администрирование заказов и платежей");
            model.addAttribute("orders", orders);
            model.addAttribute("orderStats", orderStats);
            model.addAttribute("availableStatuses", availableStatuses);

            // Параметры фильтрации
            model.addAttribute("currentPage", page);
            model.addAttribute("pageSize", size);
            model.addAttribute("totalPages", orders.getTotalPages());
            model.addAttribute("totalElements", orders.getTotalElements());
            model.addAttribute("sortBy", sortBy);
            model.addAttribute("sortDir", sortDir);
            model.addAttribute("status", status);
            model.addAttribute("search", search);
            model.addAttribute("startDate", startDate);
            model.addAttribute("endDate", endDate);

            // Навигация
            model.addAttribute("hasPrevious", orders.hasPrevious());
            model.addAttribute("hasNext", orders.hasNext());

            long loadTime = System.currentTimeMillis() - startTime;
            log.info("Orders list page loaded in {}ms", loadTime);

            // Логирование просмотра
            activityLogService.logApplicationActivity(
                    null, "ADMIN", null, null,
                    ActionType.STATE_CHANGED,
                    "Просмотр списка заказов");

            return "admin/orders";

        } catch (Exception e) {
            log.error("Error loading orders list page", e);
            model.addAttribute("error", "Ошибка загрузки списка заказов: " + e.getMessage());
            return "admin/error";
        }
    }

    /**
     * Детальный просмотр заказа
     */
    @GetMapping("/{orderId}")
    public String orderDetailPage(
            @PathVariable @NotBlank String orderId,
            Model model,
            HttpServletRequest request) {

        try {
            log.info("Loading order detail page for orderId: {}", orderId);

            // Проверка аутентификации
            if (!isValidAdminRequest(request)) {
                log.warn("Unauthorized access attempt to order detail from IP: {}",
                        request.getRemoteAddr());
                return "redirect:/admin/login";
            }

            // Валидация orderId
            if (!isValidOrderId(orderId)) {
                log.warn("Invalid orderId format: {}", orderId);
                model.addAttribute("error", "Неверный формат ID заказа");
                return "admin/error";
            }

            // Получение заказа
            Optional<OrderEntity> orderOpt = orderService.getOrderById(orderId);
            if (orderOpt.isEmpty()) {
                log.warn("Order not found: {}", orderId);
                model.addAttribute("error", "Заказ не найден");
                return "admin/error";
            }

            OrderEntity order = orderOpt.get();

            // Получение активности по заказу
            List<shit.back.entity.UserActivityLogEntity> orderActivities = activityLogService
                    .getOrderActivities(orderId);

            // Подготовка модели
            model.addAttribute("title", "Заказ " + order.getFormattedOrderId());
            model.addAttribute("subtitle", "Детальная информация о заказе");
            model.addAttribute("order", order);
            model.addAttribute("orderActivities", orderActivities);
            model.addAttribute("availableStatuses", Arrays.asList(OrderEntity.OrderStatus.values()));

            // Логирование просмотра
            activityLogService.logApplicationActivity(
                    null, "ADMIN", null, null,
                    ActionType.STATE_CHANGED,
                    "Просмотр деталей заказа " + orderId);

            return "admin/order-detail";

        } catch (Exception e) {
            log.error("Error loading order detail page for orderId: {}", orderId, e);
            model.addAttribute("error", "Ошибка загрузки деталей заказа: " + e.getMessage());
            return "admin/error";
        }
    }

    // ==================== API ЭНДПОИНТЫ ====================

    /**
     * Получение заказов с фильтрами (JSON API)
     */
    @GetMapping(value = "/api/orders", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> getOrdersApi(
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

            // Rate limiting
            RateLimitService.RateLimitResult rateLimitResult = rateLimitService.checkApiLimit(request.getRemoteAddr());
            if (!rateLimitResult.isAllowed()) {
                log.warn("Rate limit exceeded for orders API from IP: {}", request.getRemoteAddr());
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of(
                                "success", false,
                                "error", "Rate limit exceeded",
                                "message", rateLimitResult.getErrorMessage(),
                                "timestamp", LocalDateTime.now()));
            }

            // Проверка аутентификации
            if (!isValidAdminRequest(request)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of(
                                "success", false,
                                "error", "Unauthorized access",
                                "timestamp", LocalDateTime.now()));
            }

            // Валидация параметров
            if (page < 0)
                page = 0;
            if (size < 1 || size > 100)
                size = 20;

            Sort sort = createSortFromParams(sortBy, sortDir);
            Pageable pageable = PageRequest.of(page, size, sort);

            // Получение данных
            Page<OrderEntity> orders = getFilteredOrders(status, search, startDate, endDate, pageable);
            OrderService.OrderStatistics orderStats = orderService.getOrderStatistics();

            // Формирование ответа
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("orders", orders.getContent());
            response.put("pagination", Map.of(
                    "currentPage", orders.getNumber(),
                    "totalPages", orders.getTotalPages(),
                    "totalElements", orders.getTotalElements(),
                    "pageSize", orders.getSize(),
                    "hasNext", orders.hasNext(),
                    "hasPrevious", orders.hasPrevious()));
            response.put("statistics", orderStats);
            response.put("filters", Map.of(
                    "status", status,
                    "search", search,
                    "startDate", startDate,
                    "endDate", endDate,
                    "sortBy", sortBy,
                    "sortDir", sortDir));
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error in orders API", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "error", "Internal server error",
                            "message", e.getMessage(),
                            "timestamp", LocalDateTime.now()));
        }
    }

    /**
     * Изменение статуса заказа
     */
    @PostMapping("/{orderId}/status")
    @ResponseBody
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable @NotBlank String orderId,
            @RequestBody @Valid StatusUpdateRequest request,
            HttpServletRequest httpRequest) {

        try {
            log.info("Updating order {} status to {}", orderId, request.getStatus());

            // Rate limiting
            RateLimitService.RateLimitResult rateLimitResult = rateLimitService
                    .checkApiLimit(httpRequest.getRemoteAddr());
            if (!rateLimitResult.isAllowed()) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of(
                                "success", false,
                                "error", "Rate limit exceeded",
                                "message", rateLimitResult.getErrorMessage()));
            }

            // Проверка аутентификации
            if (!isValidAdminRequest(httpRequest)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "error", "Unauthorized"));
            }

            // Валидация
            if (!isValidOrderId(orderId)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "error", "Invalid order ID format"));
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
                    .body(Map.of(
                            "success", false,
                            "error", "Failed to update order status",
                            "message", e.getMessage()));
        }
    }

    /**
     * Добавление/изменение заметок к заказу
     */
    @PostMapping("/{orderId}/notes")
    @ResponseBody
    public ResponseEntity<?> updateOrderNotes(
            @PathVariable @NotBlank String orderId,
            @RequestBody @Valid NotesUpdateRequest request,
            HttpServletRequest httpRequest) {

        try {
            log.info("Updating notes for order {}", orderId);

            // Rate limiting и аутентификация
            RateLimitService.RateLimitResult rateLimitResult = rateLimitService
                    .checkApiLimit(httpRequest.getRemoteAddr());
            if (!rateLimitResult.isAllowed()) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of("success", false, "error", "Rate limit exceeded"));
            }

            if (!isValidAdminRequest(httpRequest)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "error", "Unauthorized"));
            }

            // Получение и обновление заказа
            Optional<OrderEntity> orderOpt = orderService.getOrderById(orderId);
            if (orderOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            OrderEntity order = orderOpt.get();
            order.setNotes(request.getNotes());

            // Сохранение (через orderService если есть метод save, иначе через repository)
            // Поскольку в OrderService нет метода save, добавим логику через обновление
            // статуса
            orderService.updateOrderStatus(orderId, order.getStatus());

            // Логирование
            activityLogService.logApplicationActivity(
                    null, "ADMIN", null, null,
                    ActionType.STATE_CHANGED,
                    String.format("Обновлены заметки для заказа %s", orderId));

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Order notes updated successfully",
                    "timestamp", LocalDateTime.now()));

        } catch (Exception e) {
            log.error("Error updating order notes for orderId: {}", orderId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "error", "Failed to update order notes",
                            "message", e.getMessage()));
        }
    }

    /**
     * Массовое изменение статуса заказов
     */
    @PostMapping("/batch/status")
    @ResponseBody
    public ResponseEntity<?> batchUpdateStatus(
            @RequestBody @Valid BatchStatusUpdateRequest request,
            HttpServletRequest httpRequest) {

        try {
            log.info("Batch updating status for {} orders to {}",
                    request.getOrderIds().size(), request.getStatus());

            // Rate limiting и аутентификация
            RateLimitService.RateLimitResult rateLimitResult = rateLimitService
                    .checkApiLimit(httpRequest.getRemoteAddr());
            if (!rateLimitResult.isAllowed()) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of("success", false, "error", "Rate limit exceeded"));
            }

            if (!isValidAdminRequest(httpRequest)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "error", "Unauthorized"));
            }

            // Валидация количества заказов
            if (request.getOrderIds().size() > 50) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "error", "Too many orders (max 50)"));
            }

            // Обновление заказов
            List<String> updatedOrders = new ArrayList<>();
            List<String> failedOrders = new ArrayList<>();

            for (String orderId : request.getOrderIds()) {
                try {
                    if (isValidOrderId(orderId)) {
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
                    .body(Map.of(
                            "success", false,
                            "error", "Batch update failed",
                            "message", e.getMessage()));
        }
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    /**
     * Создание параметров сортировки
     */
    private Sort createSortFromParams(String sortBy, String sortDir) {
        // Валидация параметров сортировки
        Set<String> validSortFields = Set.of(
                "createdAt", "updatedAt", "status", "finalAmount",
                "username", "orderId", "completedAt");

        if (!validSortFields.contains(sortBy)) {
            sortBy = "createdAt";
        }

        return sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
    }

    /**
     * Получение отфильтрованных заказов
     */
    private Page<OrderEntity> getFilteredOrders(String status, String search,
            String startDate, String endDate, Pageable pageable) {

        // Если есть фильтры, используем поиск, иначе получаем все
        if (hasFilters(status, search, startDate, endDate)) {
            return orderService.searchOrders(buildSearchTerm(status, search, startDate, endDate), pageable);
        } else {
            return orderService.getOrders(pageable);
        }
    }

    /**
     * Проверка наличия фильтров
     */
    private boolean hasFilters(String status, String search, String startDate, String endDate) {
        return (status != null && !status.isEmpty()) ||
                (search != null && !search.isEmpty()) ||
                (startDate != null && !startDate.isEmpty()) ||
                (endDate != null && !endDate.isEmpty());
    }

    /**
     * Построение поискового запроса
     */
    private String buildSearchTerm(String status, String search, String startDate, String endDate) {
        List<String> terms = new ArrayList<>();

        if (status != null && !status.isEmpty()) {
            terms.add("status:" + status);
        }
        if (search != null && !search.isEmpty()) {
            terms.add(search);
        }
        // Для дат можно добавить дополнительную логику

        return String.join(" ", terms);
    }

    /**
     * Валидация формата ID заказа
     */
    private boolean isValidOrderId(String orderId) {
        return orderId != null &&
                orderId.matches("^[A-Z0-9]{8}$") &&
                orderId.length() == 8;
    }

    /**
     * Безопасная проверка администраторского доступа через API ключ
     * Использует криптографически стойкое сравнение для предотвращения timing
     * attacks
     */
    private boolean isValidAdminRequest(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();

        try {
            // Получаем API ключ из конфигурации
            String expectedApiKey = securityProperties.getApi().getKey();

            // Проверяем, что API ключ настроен
            if (expectedApiKey == null || expectedApiKey.trim().isEmpty()) {
                log.error("Admin API key is not configured. Set API_SECRET_KEY environment variable");
                return false;
            }

            // Получаем API ключ из заголовка запроса
            String providedApiKey = request.getHeader("X-Admin-API-Key");

            // Проверяем наличие ключа в запросе
            if (providedApiKey == null || providedApiKey.trim().isEmpty()) {
                log.warn("Admin access attempt without API key from IP: {}", remoteAddr);
                return false;
            }

            // Используем constant-time сравнение для предотвращения timing attacks
            boolean isValid = constantTimeEquals(expectedApiKey, providedApiKey);

            if (isValid) {
                log.debug("Valid admin API key provided from IP: {}", remoteAddr);
                return true;
            } else {
                log.warn("Invalid admin API key provided from IP: {}", remoteAddr);
                return false;
            }

        } catch (Exception e) {
            log.error("Error during admin authentication from IP: {}", remoteAddr, e);
            return false;
        }
    }

    /**
     * Constant-time сравнение строк для предотвращения timing attacks
     */
    private boolean constantTimeEquals(String expected, String provided) {
        if (expected == null || provided == null) {
            return false;
        }

        byte[] expectedBytes = expected.getBytes();
        byte[] providedBytes = provided.getBytes();

        // Если длины не совпадают, всё равно выполняем полное сравнение
        // для поддержания constant-time
        int expectedLength = expectedBytes.length;
        int providedLength = providedBytes.length;
        int maxLength = Math.max(expectedLength, providedLength);

        int result = expectedLength ^ providedLength;

        for (int i = 0; i < maxLength; i++) {
            byte expectedByte = (i < expectedLength) ? expectedBytes[i] : 0;
            byte providedByte = (i < providedLength) ? providedBytes[i] : 0;
            result |= expectedByte ^ providedByte;
        }

        return result == 0;
    }

    // ==================== DTO КЛАССЫ ====================

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
     * Запрос на обновление заметок заказа
     */
    @lombok.Data
    public static class NotesUpdateRequest {
        @Size(max = 2000, message = "Notes must not exceed 2000 characters")
        private String notes;
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