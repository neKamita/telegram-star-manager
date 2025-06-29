package shit.back.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shit.back.dto.order.BatchUpdateResult;
import shit.back.entity.BalanceTransactionEntity;
import shit.back.entity.OrderEntity;
import shit.back.entity.UserActivityLogEntity.ActionType;
import shit.back.model.Order;
import shit.back.repository.OrderJpaRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Сервис для обновления заказов
 * Следует принципу Single Responsibility (SRP)
 */
@Slf4j
@Service
@Transactional
public class OrderUpdateService {

    @Autowired
    private OrderJpaRepository orderRepository;

    @Autowired
    private UserActivityLogService activityLogService;

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private OrderValidationService orderValidationService;

    /**
     * Создание нового заказа с проверкой баланса
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
     * Создание заказа с проверкой баланса
     */
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
     * Обновление статуса заказа
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
     * Обновление заметок заказа
     */
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
     * Массовое обновление статуса заказов
     */
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
                if (!orderValidationService.validateStatusTransition(currentStatus, newStatus)) {
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
     * Обработка оплаты балансом
     */
    public OrderEntity processBalancePayment(String orderId, Long userId) {
        log.info("Обработка оплаты балансом для заказа {} пользователя {}", orderId, userId);

        try {
            Optional<OrderEntity> orderOpt = orderRepository.findById(orderId);
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
     */
    public OrderEntity processMixedPayment(String orderId, Long userId,
            BigDecimal balanceAmount, BigDecimal externalAmount) {
        log.info("Обработка смешанной оплаты для заказа {}: баланс={}, внешняя={}",
                orderId, balanceAmount, externalAmount);

        try {
            Optional<OrderEntity> orderOpt = orderRepository.findById(orderId);
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
     */
    public OrderEntity cancelOrderWithBalanceRefund(String orderId, Long userId, String reason) {
        log.info("Отмена заказа {} с возвратом на баланс для пользователя {}: {}",
                orderId, userId, reason);

        try {
            Optional<OrderEntity> orderOpt = orderRepository.findById(orderId);
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

    /**
     * Маппинг статуса заказа в тип действия для логирования
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
}