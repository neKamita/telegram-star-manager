package shit.back.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import shit.back.entity.OrderEntity;

import java.util.List;

/**
 * Сервис для валидации заказов и бизнес-правил
 * Следует принципу Single Responsibility (SRP)
 */
@Slf4j
@Service
public class OrderValidationService {

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
     * Проверка возможности отмены заказа
     */
    public boolean canCancelOrder(OrderEntity order) {
        if (order == null) {
            return false;
        }
        return order.isCancellable();
    }

    /**
     * Проверка возможности возврата средств
     */
    public boolean canRefundOrder(OrderEntity order) {
        if (order == null) {
            return false;
        }
        return order.getStatus() == OrderEntity.OrderStatus.COMPLETED ||
                order.getStatus() == OrderEntity.OrderStatus.PAYMENT_RECEIVED;
    }

    /**
     * Валидация данных заказа при создании
     */
    public boolean validateOrderData(OrderEntity order) {
        if (order == null) {
            log.warn("Order is null");
            return false;
        }

        if (order.getUserId() == null) {
            log.warn("Order userId is null");
            return false;
        }

        if (order.getFinalAmount() == null || order.getFinalAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            log.warn("Order amount is invalid: {}", order.getFinalAmount());
            return false;
        }

        if (order.getStarCount() == null || order.getStarCount() <= 0) {
            log.warn("Order star count is invalid: {}", order.getStarCount());
            return false;
        }

        return true;
    }

    /**
     * Проверка лимитов для массовых операций
     */
    public boolean validateBatchOperationLimits(int itemCount, int maxAllowed) {
        return itemCount > 0 && itemCount <= maxAllowed;
    }
}