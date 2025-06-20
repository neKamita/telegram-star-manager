package shit.back.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shit.back.entity.UserBalanceEntity;
import shit.back.entity.BalanceTransactionEntity;
import shit.back.repository.UserBalanceJpaRepository;
import shit.back.repository.BalanceTransactionJpaRepository;

import java.util.List;

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

    // --- Остальные методы интерфейса (реализация-заглушка, чтобы не менять
    // бизнес-логику) ---

    @Override
    public boolean checkSufficientBalance(Long userId, java.math.BigDecimal amount) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public BalanceTransactionEntity processBalancePayment(Long userId, String orderId, java.math.BigDecimal amount) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void refundToBalance(Long userId, java.math.BigDecimal amount, String orderId, String reason) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void releaseReservedBalance(Long userId, String orderId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public BalanceTransactionEntity withdraw(Long userId, java.math.BigDecimal amount, String reason) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public java.util.Map<String, Object> getBalanceStatistics(Long userId) {
        return java.util.Collections.emptyMap();
    }

    @Override
    public void reserveBalance(Long userId, java.math.BigDecimal amount, String reason) {
        throw new UnsupportedOperationException("Not implemented");
    }
}