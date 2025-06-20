package shit.back.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shit.back.entity.BalanceTransactionEntity;
import shit.back.entity.OrderEntity;
import shit.back.model.Order;
import shit.back.repository.OrderJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class OrderCreationService {

    private static final Logger log = LoggerFactory.getLogger(OrderCreationService.class);

    @Autowired
    private OrderJpaRepository orderRepository;
    @Autowired
    private OrderService orderService;
    @Autowired
    private BalanceService balanceService;
    @Autowired
    private UserActivityLogService activityLogService;

    /**
     * Создание заказа с проверкой баланса
     */
    @Transactional
    public OrderEntity createOrderWithBalanceCheck(Order order) {
        // Делегируем создание заказа сервису, чтобы не нарушать инкапсуляцию и SRP
        try {
            return orderService.createOrderWithBalanceCheck(order);
        } catch (Exception e) {
            log.error("Ошибка при создании заказа с проверкой баланса для пользователя {}: {}", order.getUserId(),
                    e.getMessage(), e);
            throw OrderErrorHandler.handleOrderCreationException(e);
        }
    }

    /**
     * Обработка оплаты балансом
     */
    @Transactional
    public OrderEntity processBalancePayment(String orderId, Long userId) {
        log.info("Обработка оплаты балансом для заказа {} пользователя {}", orderId, userId);
        try {
            OrderEntity order = orderService.getOrderById(orderId)
                    .orElseThrow(() -> new RuntimeException("Заказ не найден: " + orderId));
            if (!order.getUserId().equals(userId)) {
                throw new RuntimeException("Заказ принадлежит другому пользователю");
            }
            BalanceTransactionEntity transaction = balanceService.processBalancePayment(userId, orderId,
                    order.getFinalAmount());
            order.setBalancePaymentInfo(transaction.getTransactionId(), order.getFinalAmount());
            order.updateStatus(OrderEntity.OrderStatus.PAYMENT_RECEIVED);
            OrderEntity savedOrder = orderRepository.save(order);
            activityLogService.logOrderActivity(
                    userId, order.getUsername(), null, null,
                    shit.back.entity.UserActivityLogEntity.ActionType.PAYMENT_COMPLETED,
                    "Оплата заказа балансом: " + order.getFinalAmount(),
                    orderId, order.getFinalAmount(), order.getStarCount(),
                    "BALANCE");
            log.info("Оплата балансом завершена для заказа {}: транзакция {}", orderId, transaction.getTransactionId());
            return savedOrder;
        } catch (Exception e) {
            log.error("Ошибка при обработке оплаты балансом для заказа {}: {}", orderId, e.getMessage(), e);
            throw OrderErrorHandler.handleOrderPaymentException(e);
        }
    }

    /**
     * Обработка комбинированной оплаты (баланс + внешняя)
     */
    @Transactional
    public OrderEntity processMixedPayment(String orderId, Long userId, java.math.BigDecimal balanceAmount,
            java.math.BigDecimal externalAmount) {
        log.info("Обработка смешанной оплаты для заказа {}: баланс={}, внешняя={}", orderId, balanceAmount,
                externalAmount);
        try {
            OrderEntity order = orderService.getOrderById(orderId)
                    .orElseThrow(() -> new RuntimeException("Заказ не найден: " + orderId));
            if (!order.getUserId().equals(userId)) {
                throw new RuntimeException("Заказ принадлежит другому пользователю");
            }
            java.math.BigDecimal totalPayment = balanceAmount.add(externalAmount);
            if (totalPayment.compareTo(order.getFinalAmount()) != 0) {
                throw new RuntimeException("Сумма платежей не соответствует стоимости заказа");
            }
            BalanceTransactionEntity balanceTransaction = null;
            if (balanceAmount.compareTo(java.math.BigDecimal.ZERO) > 0) {
                balanceTransaction = balanceService.processBalancePayment(userId, orderId, balanceAmount);
            }
            order.setBalancePaymentInfo(
                    balanceTransaction != null ? balanceTransaction.getTransactionId() : null,
                    balanceAmount);
            order.setExternalPaymentInfo(externalAmount);
            order.updateStatus(OrderEntity.OrderStatus.PARTIAL_BALANCE_PAYMENT);
            OrderEntity savedOrder = orderRepository.save(order);
            activityLogService.logOrderActivity(
                    userId, order.getUsername(), null, null,
                    shit.back.entity.UserActivityLogEntity.ActionType.PAYMENT_INITIATED,
                    String.format("Смешанная оплата заказа: баланс=%s, внешняя=%s", balanceAmount, externalAmount),
                    orderId, order.getFinalAmount(), order.getStarCount(),
                    "MIXED");
            log.info("Смешанная оплата обработана для заказа {}: баланс={}, внешняя={}", orderId, balanceAmount,
                    externalAmount);
            return savedOrder;
        } catch (Exception e) {
            log.error("Ошибка при обработке смешанной оплаты для заказа {}: {}", orderId, e.getMessage(), e);
            throw OrderErrorHandler.handleOrderPaymentException(e);
        }
    }

    /**
     * Отмена заказа с возвратом средств на баланс
     */
    @Transactional
    public OrderEntity cancelOrderWithBalanceRefund(String orderId, Long userId, String reason) {
        log.info("Отмена заказа {} с возвратом на баланс для пользователя {}: {}", orderId, userId, reason);
        try {
            OrderEntity order = orderService.getOrderById(orderId)
                    .orElseThrow(() -> new RuntimeException("Заказ не найден: " + orderId));
            if (!order.getUserId().equals(userId)) {
                throw new RuntimeException("Заказ принадлежит другому пользователю");
            }
            if (!order.isCancellable()) {
                throw new RuntimeException("Заказ нельзя отменить в текущем статусе: " + order.getStatus());
            }
            if (order.isBalanceUsed() && order.getBalanceUsedAmount().compareTo(java.math.BigDecimal.ZERO) > 0) {
                balanceService.refundToBalance(userId, order.getBalanceUsedAmount(), orderId,
                        "Возврат за отмененный заказ: " + reason);
                log.info("Возвращены средства на баланс для заказа {}: {}", orderId, order.getBalanceUsedAmount());
            }
            balanceService.releaseReservedBalance(userId, orderId);
            order.updateStatus(OrderEntity.OrderStatus.CANCELLED);
            OrderEntity savedOrder = orderRepository.save(order);
            activityLogService.logOrderActivity(
                    userId, order.getUsername(), null, null,
                    shit.back.entity.UserActivityLogEntity.ActionType.ORDER_CANCELLED,
                    "Заказ отменен с возвратом на баланс: " + reason,
                    orderId, order.getFinalAmount(), order.getStarCount(),
                    order.getPaymentMethod());
            log.info("Заказ {} успешно отменен с возвратом средств на баланс", orderId);
            return savedOrder;
        } catch (Exception e) {
            log.error("Ошибка при отмене заказа {} с возвратом на баланс: {}", orderId, e.getMessage(), e);
            throw OrderErrorHandler.handleOrderCancelException(e);
        }
    }
}