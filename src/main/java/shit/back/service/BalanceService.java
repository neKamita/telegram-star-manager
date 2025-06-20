package shit.back.service;

import shit.back.entity.BalanceTransactionEntity;
import java.math.BigDecimal;
import shit.back.entity.UserBalanceEntity;
import java.util.List;

public interface BalanceService {
    boolean checkSufficientBalance(Long userId, BigDecimal amount);

    BalanceTransactionEntity processBalancePayment(Long userId, String orderId, BigDecimal amount);

    void refundToBalance(Long userId, BigDecimal amount, String orderId, String reason);

    void releaseReservedBalance(Long userId, String orderId);

    /**
     * Получить существующий баланс пользователя или создать новый, если не найден.
     */
    UserBalanceEntity getOrCreateBalance(Long userId);

    /**
     * Получить историю транзакций пользователя (по убыванию времени, лимит).
     */
    List<BalanceTransactionEntity> getBalanceHistory(Long userId, int limit);

    BalanceTransactionEntity withdraw(Long userId, BigDecimal amount, String reason);

    java.util.Map<String, Object> getBalanceStatistics(Long userId);

    void reserveBalance(Long userId, BigDecimal amount, String reason);
}