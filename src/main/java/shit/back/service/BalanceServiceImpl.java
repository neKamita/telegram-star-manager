package shit.back.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shit.back.entity.UserBalanceEntity;
import shit.back.entity.BalanceTransactionEntity;
import shit.back.repository.UserBalanceJpaRepository;
import shit.back.repository.BalanceTransactionJpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
public class BalanceServiceImpl implements BalanceService {

    private final UserBalanceJpaRepository userBalanceRepository;
    private final BalanceTransactionJpaRepository transactionRepository;

    @Autowired
    public BalanceServiceImpl(UserBalanceJpaRepository userBalanceRepository,
            BalanceTransactionJpaRepository transactionRepository) {
        this.userBalanceRepository = userBalanceRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    @Transactional
    public UserBalanceEntity getOrCreateBalance(Long userId) {
        return userBalanceRepository.findByUserId(userId)
                .orElseGet(() -> userBalanceRepository.save(new UserBalanceEntity(userId)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BalanceTransactionEntity> getBalanceHistory(Long userId, int limit) {
        return transactionRepository.findTopByUserIdOrderByCreatedAtDesc(userId, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean checkSufficientBalance(Long userId, BigDecimal amount) {
        UserBalanceEntity balance = getOrCreateBalance(userId);
        return balance.getCurrentBalance().compareTo(amount) >= 0;
    }

    @Override
    @Transactional
    public BalanceTransactionEntity processBalancePayment(Long userId, String orderId, BigDecimal amount) {
        UserBalanceEntity balance = getOrCreateBalance(userId);

        if (!checkSufficientBalance(userId, amount)) {
            throw new IllegalArgumentException("Insufficient balance");
        }

        balance.withdraw(amount);
        userBalanceRepository.save(balance);

        BalanceTransactionEntity transaction = new BalanceTransactionEntity();
        transaction.setUserId(userId);
        transaction.setAmount(amount.negate()); // Negative for withdrawal
        transaction.setDescription("Payment for order: " + orderId);
        transaction.setOrderId(orderId);

        return transactionRepository.save(transaction);
    }

    @Override
    @Transactional
    public void refundToBalance(Long userId, BigDecimal amount, String orderId, String reason) {
        UserBalanceEntity balance = getOrCreateBalance(userId);
        balance.deposit(amount);
        userBalanceRepository.save(balance);

        BalanceTransactionEntity transaction = new BalanceTransactionEntity();
        transaction.setUserId(userId);
        transaction.setAmount(amount); // Positive for deposit
        transaction.setDescription("Refund: " + reason);
        transaction.setOrderId(orderId);

        transactionRepository.save(transaction);
    }

    @Override
    @Transactional
    public void releaseReservedBalance(Long userId, String orderId) {
        // Implementation for releasing reserved balance
        // For now, just log the action
        // In full implementation, this would release funds from reserved state
    }

    @Override
    @Transactional
    public BalanceTransactionEntity withdraw(Long userId, BigDecimal amount, String reason) {
        UserBalanceEntity balance = getOrCreateBalance(userId);

        if (!checkSufficientBalance(userId, amount)) {
            throw new IllegalArgumentException("Insufficient balance for withdrawal");
        }

        balance.withdraw(amount);
        userBalanceRepository.save(balance);

        BalanceTransactionEntity transaction = new BalanceTransactionEntity();
        transaction.setUserId(userId);
        transaction.setAmount(amount.negate()); // Negative for withdrawal
        transaction.setDescription(reason);

        return transactionRepository.save(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getBalanceStatistics(Long userId) {
        UserBalanceEntity balance = getOrCreateBalance(userId);
        Map<String, Object> stats = new HashMap<>();

        stats.put("currentBalance", balance.getCurrentBalance());
        stats.put("userId", userId);
        stats.put("isActive", balance.getIsActive());

        return stats;
    }

    @Override
    @Transactional
    public void reserveBalance(Long userId, BigDecimal amount, String reason) {
        UserBalanceEntity balance = getOrCreateBalance(userId);

        if (!checkSufficientBalance(userId, amount)) {
            throw new IllegalArgumentException("Insufficient balance to reserve");
        }

        // Implementation for reserving balance
        // For now, just log the action
        // In full implementation, this would move funds to reserved state
    }
}