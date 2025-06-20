package shit.back.service;

import shit.back.entity.OrderEntity;
import shit.back.model.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import shit.back.dto.order.BatchUpdateResult;

public interface OrderCommandService {
    OrderEntity createOrder(Order order);

    Optional<OrderEntity> updateOrderStatus(String orderId, OrderEntity.OrderStatus status);

    Optional<OrderEntity> updateOrderNotes(String orderId, String notes, String updatedBy);

    BatchUpdateResult batchUpdateStatus(List<String> orderIds,
            OrderEntity.OrderStatus newStatus, String updatedBy);

    OrderEntity createOrderWithBalanceCheck(Order order);

    OrderEntity processBalancePayment(String orderId, Long userId);

    OrderEntity processMixedPayment(String orderId, Long userId, BigDecimal balanceAmount, BigDecimal externalAmount);

    OrderEntity cancelOrderWithBalanceRefund(String orderId, Long userId, String reason);
}