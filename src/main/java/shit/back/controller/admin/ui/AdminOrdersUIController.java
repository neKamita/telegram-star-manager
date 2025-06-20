package shit.back.controller.admin.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import shit.back.controller.admin.shared.AdminControllerOperations;
import shit.back.entity.OrderEntity;
import shit.back.entity.UserActivityLogEntity.ActionType;
import shit.back.service.OrderService;
import shit.back.service.UserActivityLogService;
import shit.back.service.admin.shared.AdminAuthenticationService;
import shit.back.service.admin.shared.AdminValidationService;
import shit.back.service.admin.shared.AdminSecurityHelper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.*;
import shit.back.dto.order.OrderStatistics;

/**
 * UI контроллер для управления заказами
 * Содержит только HTML представления для заказов
 * Следует принципам SOLID и чистой архитектуры
 */
@Controller
@RequestMapping("/admin/orders")
@Validated
public class AdminOrdersUIController implements AdminControllerOperations {

    private static final Logger log = LoggerFactory.getLogger(AdminOrdersUIController.class);

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserActivityLogService activityLogService;

    @Autowired
    private AdminAuthenticationService adminAuthenticationService;

    @Autowired
    private AdminValidationService adminValidationService;

    @Autowired
    private AdminSecurityHelper adminSecurityHelper;

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

            // Проверка аутентификации через унифицированный сервис
            if (!adminAuthenticationService.validateAuthentication(request)) {
                log.warn("Unauthorized access attempt to orders page from IP: {}",
                        adminSecurityHelper.getClientIpAddress(request));
                return "redirect:/admin/login";
            }

            // Проверка подозрительной активности
            if (adminSecurityHelper.isSuspiciousActivity(request)) {
                log.warn("Suspicious activity detected on orders page");
                model.addAttribute("warning", "Подозрительная активность обнаружена");
            }

            // Валидация параметров через унифицированный сервис
            Map<String, Object> validationResult = adminValidationService.validatePaginationParams(page, size);
            page = (Integer) validationResult.get("page");
            size = (Integer) validationResult.get("size");

            // Валидация поискового запроса
            Map<String, Object> searchValidation = adminValidationService.validateSearchQuery(search);
            if (!(Boolean) searchValidation.get("valid")) {
                model.addAttribute("error", searchValidation.get("error"));
                search = "";
            } else {
                search = (String) searchValidation.get("sanitized");
            }

            // Валидация диапазона дат
            Map<String, Object> dateValidation = adminValidationService.validateDateRange(startDate, endDate);
            if (!(Boolean) dateValidation.get("valid")) {
                model.addAttribute("warning", dateValidation.get("error"));
                startDate = null;
                endDate = null;
            }

            // Создание параметров сортировки через унифицированный сервис
            Sort sort = adminValidationService.createValidSort(sortBy, sortDir, getValidSortFields());
            Pageable pageable = PageRequest.of(page, size, sort);

            // Получение заказов с фильтрами
            Page<OrderEntity> orders = getFilteredOrders(status, search, startDate, endDate, pageable);

            // Получение оптимизированной статистики заказов
            OrderStatistics orderStats = orderService.getOrderStatisticsOptimized();

            // Статусы для фильтра
            List<OrderEntity.OrderStatus> availableStatuses = Arrays.asList(OrderEntity.OrderStatus.values());

            // Подготовка модели
            model.addAttribute("title", "Управление заказами");
            model.addAttribute("subtitle", "Администрирование заказов и платежей");
            model.addAttribute("orders", orders);
            model.addAttribute("orderStats", orderStats);
            model.addAttribute("availableStatuses", availableStatuses);

            // Параметры фильтрации (санитизированные)
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

            // Информация о производительности
            long loadTime = System.currentTimeMillis() - startTime;
            model.addAttribute("loadTime", loadTime);
            log.info("Orders list page loaded in {}ms", loadTime);

            // Логирование просмотра через security helper
            adminSecurityHelper.logAdminActivity(request, "VIEW_ORDERS_LIST",
                    "Просмотр списка заказов, фильтры: " + buildFiltersDescription(status, search, startDate, endDate));

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
            if (!adminAuthenticationService.validateAuthentication(request)) {
                log.warn("Unauthorized access attempt to order detail from IP: {}",
                        adminSecurityHelper.getClientIpAddress(request));
                return "redirect:/admin/login";
            }

            // Валидация orderId через унифицированный сервис
            if (!adminValidationService.isValidOrderId(orderId)) {
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

            // Добавление контекста безопасности
            Map<String, Object> securityContext = adminSecurityHelper.createSecurityContext(request);
            model.addAttribute("securityContext", securityContext);

            // Логирование просмотра через security helper
            adminSecurityHelper.logAdminActivity(request, "VIEW_ORDER_DETAIL",
                    "Просмотр деталей заказа " + orderId);

            return "admin/order-detail";

        } catch (Exception e) {
            log.error("Error loading order detail page for orderId: {}", orderId, e);
            model.addAttribute("error", "Ошибка загрузки деталей заказа: " + e.getMessage());
            return "admin/error";
        }
    }

    /**
     * Форма для редактирования заказа
     */
    @GetMapping("/{orderId}/edit")
    public String editOrderPage(
            @PathVariable @NotBlank String orderId,
            Model model,
            HttpServletRequest request) {

        try {
            log.info("Loading order edit page for orderId: {}", orderId);

            // Проверка аутентификации
            if (!adminAuthenticationService.validateAuthentication(request)) {
                return "redirect:/admin/login";
            }

            // Валидация orderId
            if (!adminValidationService.isValidOrderId(orderId)) {
                model.addAttribute("error", "Неверный формат ID заказа");
                return "admin/error";
            }

            // Получение заказа
            Optional<OrderEntity> orderOpt = orderService.getOrderById(orderId);
            if (orderOpt.isEmpty()) {
                model.addAttribute("error", "Заказ не найден");
                return "admin/error";
            }

            OrderEntity order = orderOpt.get();

            // Подготовка модели для редактирования
            model.addAttribute("title", "Редактирование заказа " + order.getFormattedOrderId());
            model.addAttribute("subtitle", "Изменение параметров заказа");
            model.addAttribute("order", order);
            model.addAttribute("availableStatuses", Arrays.asList(OrderEntity.OrderStatus.values()));
            model.addAttribute("action", "edit");

            // Логирование доступа к форме редактирования
            adminSecurityHelper.logAdminActivity(request, "ACCESS_ORDER_EDIT",
                    "Доступ к форме редактирования заказа " + orderId);

            return "admin/order-edit";

        } catch (Exception e) {
            log.error("Error loading order edit page for orderId: {}", orderId, e);
            model.addAttribute("error", "Ошибка загрузки формы редактирования: " + e.getMessage());
            return "admin/error";
        }
    }

    /**
     * Обновление заказа (POST обработка формы)
     */
    @PostMapping("/{orderId}/update")
    public String updateOrder(
            @PathVariable @NotBlank String orderId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) String reason,
            RedirectAttributes redirectAttributes,
            HttpServletRequest request) {

        try {
            log.info("Updating order {} via UI form", orderId);

            // Проверка аутентификации
            if (!adminAuthenticationService.validateAuthentication(request)) {
                return "redirect:/admin/login";
            }

            // Валидация orderId
            if (!adminValidationService.isValidOrderId(orderId)) {
                redirectAttributes.addFlashAttribute("error", "Неверный формат ID заказа");
                return "redirect:/admin/orders";
            }

            // Санитизация входных данных
            notes = adminSecurityHelper.sanitizeInput(notes);
            reason = adminSecurityHelper.sanitizeInput(reason);

            boolean updated = false;
            StringBuilder updateLog = new StringBuilder();

            // Обновление статуса, если указан
            if (status != null && !status.trim().isEmpty()) {
                try {
                    OrderEntity.OrderStatus newStatus = OrderEntity.OrderStatus.valueOf(status);
                    Optional<OrderEntity> updatedOrder = orderService.updateOrderStatus(orderId, newStatus);

                    if (updatedOrder.isPresent()) {
                        updated = true;
                        updateLog.append("Статус изменен на ").append(newStatus);

                        // Логирование изменения статуса
                        activityLogService.logApplicationActivity(
                                null, "ADMIN", null, null,
                                ActionType.STATE_CHANGED,
                                String.format("Изменен статус заказа %s на %s через UI. Причина: %s",
                                        orderId, newStatus, reason != null ? reason : "Не указана"));
                    }
                } catch (IllegalArgumentException e) {
                    redirectAttributes.addFlashAttribute("error", "Неверный статус заказа");
                    return "redirect:/admin/orders/" + orderId;
                }
            }

            // TODO: Здесь можно добавить обновление заметок, если OrderService поддерживает
            // это

            if (updated) {
                redirectAttributes.addFlashAttribute("success",
                        "Заказ успешно обновлен: " + updateLog.toString());

                // Логирование общего обновления
                adminSecurityHelper.logAdminActivity(request, "UPDATE_ORDER",
                        "Обновление заказа " + orderId + ": " + updateLog.toString());
            } else {
                redirectAttributes.addFlashAttribute("info", "Изменения не внесены");
            }

            return "redirect:/admin/orders/" + orderId;

        } catch (Exception e) {
            log.error("Error updating order {} via UI", orderId, e);
            redirectAttributes.addFlashAttribute("error", "Ошибка обновления заказа: " + e.getMessage());
            return "redirect:/admin/orders/" + orderId;
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

    private String buildFiltersDescription(String status, String search, String startDate, String endDate) {
        List<String> filters = new ArrayList<>();

        if (status != null && !status.isEmpty()) {
            filters.add("статус=" + status);
        }
        if (search != null && !search.isEmpty()) {
            filters.add("поиск=" + search);
        }
        if (startDate != null && !startDate.isEmpty()) {
            filters.add("с=" + startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            filters.add("до=" + endDate);
        }

        return filters.isEmpty() ? "без фильтров" : String.join(", ", filters);
    }

    @Override
    public Map<String, Object> createErrorResponse(String message, Exception e) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        response.put("message", e != null ? e.getMessage() : "Unknown error");
        response.put("timestamp", LocalDateTime.now());
        return response;
    }
}