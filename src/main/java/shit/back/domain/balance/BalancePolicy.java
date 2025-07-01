package shit.back.domain.balance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import shit.back.domain.balance.exceptions.InvalidTransactionException;
import shit.back.domain.balance.valueobjects.Currency;
import shit.back.domain.balance.valueobjects.Money;
import shit.back.entity.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Политики и бизнес-правила для операций с балансом
 * 
 * Инкапсулирует все бизнес-ограничения, лимиты и правила валидации
 * для обеспечения корректности операций с балансом пользователей.
 */
@Component
public class BalancePolicy {

    // Rate limiting tracking
    private final Map<Long, UserRateLimitInfo> userRateLimits = new ConcurrentHashMap<>();

    // Выполнение операций

    // Минимальные суммы операций
    @Value("${balance.policy.min-deposit-amount:1.00}")
    private BigDecimal minDepositAmount;

    @Value("${balance.policy.min-withdrawal-amount:0.01}")
    private BigDecimal minWithdrawalAmount;

    @Value("${balance.policy.min-transfer-amount:0.01}")
    private BigDecimal minTransferAmount;

    // Максимальные суммы операций
    @Value("${balance.policy.max-deposit-amount:10000.00}")
    private BigDecimal maxDepositAmount;

    @Value("${balance.policy.max-withdrawal-amount:5000.00}")
    private BigDecimal maxWithdrawalAmount;

    @Value("${balance.policy.max-transfer-amount:3000.00}")
    private BigDecimal maxTransferAmount;

    // Дневные лимиты
    @Value("${balance.policy.daily-deposit-limit:50000.00}")
    private BigDecimal dailyDepositLimit;

    @Value("${balance.policy.daily-withdrawal-limit:20000.00}")
    private BigDecimal dailyWithdrawalLimit;

    @Value("${balance.policy.daily-transaction-count-limit:100}")
    private int dailyTransactionCountLimit;

    // Rate limiting
    @Value("${balance.policy.max-transactions-per-minute:10}")
    private int maxTransactionsPerMinute;

    @Value("${balance.policy.min-interval-between-transactions-seconds:1}")
    private int minIntervalBetweenTransactionsSeconds;

    // Валютные ограничения
    @Value("${balance.policy.max-currency-conversion-fee-percent:3.0}")
    private double maxCurrencyConversionFeePercent;

    @Value("${balance.policy.allow-cross-currency-transactions:true}")
    private boolean allowCrossCurrencyTransactions;

    /**
     * Валидация суммы пополнения
     */
    public void validateDepositAmount(Money amount) {
        if (amount == null) {
            throw new InvalidTransactionException("DEPOSIT_AMOUNT_NULL", "Сумма пополнения не может быть null",
                    "Положительная сумма");
        }

        if (amount.isZero()) {
            throw new InvalidTransactionException("DEPOSIT_AMOUNT_ZERO", "0",
                    String.format("Минимум %s", Money.of(minDepositAmount)));
        }

        if (amount.getAmount().compareTo(minDepositAmount) < 0) {
            throw new InvalidTransactionException("DEPOSIT_AMOUNT_TOO_SMALL",
                    amount.getFormattedAmount(),
                    String.format("Минимум %s", Money.of(minDepositAmount).getFormattedAmount()));
        }

        if (amount.getAmount().compareTo(maxDepositAmount) > 0) {
            throw new InvalidTransactionException("DEPOSIT_AMOUNT_TOO_LARGE",
                    amount.getFormattedAmount(),
                    String.format("Максимум %s", Money.of(maxDepositAmount).getFormattedAmount()));
        }
    }

    /**
     * Валидация суммы списания
     */
    public void validateWithdrawalAmount(Money amount) {
        if (amount == null) {
            throw new InvalidTransactionException("WITHDRAWAL_AMOUNT_NULL", "Сумма списания не может быть null",
                    "Положительная сумма");
        }

        if (amount.isZero()) {
            throw new InvalidTransactionException("WITHDRAWAL_AMOUNT_ZERO", "0",
                    String.format("Минимум %s", Money.of(minWithdrawalAmount)));
        }

        if (amount.getAmount().compareTo(minWithdrawalAmount) < 0) {
            throw new InvalidTransactionException("WITHDRAWAL_AMOUNT_TOO_SMALL",
                    amount.getFormattedAmount(),
                    String.format("Минимум %s", Money.of(minWithdrawalAmount).getFormattedAmount()));
        }

        if (amount.getAmount().compareTo(maxWithdrawalAmount) > 0) {
            throw new InvalidTransactionException("WITHDRAWAL_AMOUNT_TOO_LARGE",
                    amount.getFormattedAmount(),
                    String.format("Максимум %s", Money.of(maxWithdrawalAmount).getFormattedAmount()));
        }
    }

    /**
     * Валидация суммы перевода
     */
    public void validateTransferAmount(Money amount) {
        if (amount == null) {
            throw new InvalidTransactionException("TRANSFER_AMOUNT_NULL", "Сумма перевода не может быть null",
                    "Положительная сумма");
        }

        if (amount.isZero()) {
            throw new InvalidTransactionException("TRANSFER_AMOUNT_ZERO", "0",
                    String.format("Минимум %s", Money.of(minTransferAmount)));
        }

        if (amount.getAmount().compareTo(minTransferAmount) < 0) {
            throw new InvalidTransactionException("TRANSFER_AMOUNT_TOO_SMALL",
                    amount.getFormattedAmount(),
                    String.format("Минимум %s", Money.of(minTransferAmount).getFormattedAmount()));
        }

        if (amount.getAmount().compareTo(maxTransferAmount) > 0) {
            throw new InvalidTransactionException("TRANSFER_AMOUNT_TOO_LARGE",
                    amount.getFormattedAmount(),
                    String.format("Максимум %s", Money.of(maxTransferAmount).getFormattedAmount()));
        }
    }

    /**
     * Валидация дневных лимитов
     */
    public void validateDailyLimits(TransactionType type, Money amount, Money totalTodayAmount,
            int todayTransactionCount) {
        // Проверка лимита количества транзакций
        if (todayTransactionCount >= dailyTransactionCountLimit) {
            throw new InvalidTransactionException("DAILY_TRANSACTION_COUNT_LIMIT_EXCEEDED",
                    String.valueOf(todayTransactionCount + 1),
                    String.valueOf(dailyTransactionCountLimit));
        }

        // Проверка дневных лимитов по сумме
        Money newDailyTotal = totalTodayAmount.add(amount);

        switch (type) {
            case DEPOSIT:
                if (newDailyTotal.getAmount().compareTo(dailyDepositLimit) > 0) {
                    throw new InvalidTransactionException("DAILY_DEPOSIT_LIMIT_EXCEEDED",
                            newDailyTotal.getFormattedAmount(),
                            Money.of(dailyDepositLimit).getFormattedAmount());
                }
                break;
            case WITHDRAWAL:
            case PURCHASE:
                if (newDailyTotal.getAmount().compareTo(dailyWithdrawalLimit) > 0) {
                    throw new InvalidTransactionException("DAILY_WITHDRAWAL_LIMIT_EXCEEDED",
                            newDailyTotal.getFormattedAmount(),
                            Money.of(dailyWithdrawalLimit).getFormattedAmount());
                }
                break;
        }
    }

    /**
     * Валидация rate limiting
     */
    public void validateRateLimit(int transactionsInLastMinute, LocalDateTime lastTransactionTime) {
        // Проверка количества транзакций в минуту
        if (transactionsInLastMinute >= maxTransactionsPerMinute) {
            throw new InvalidTransactionException("RATE_LIMIT_EXCEEDED",
                    String.valueOf(transactionsInLastMinute),
                    String.valueOf(maxTransactionsPerMinute));
        }

        // Проверка минимального интервала между транзакциями
        if (lastTransactionTime != null) {
            long secondsSinceLastTransaction = ChronoUnit.SECONDS.between(lastTransactionTime, LocalDateTime.now());
            if (secondsSinceLastTransaction < minIntervalBetweenTransactionsSeconds) {
                throw new InvalidTransactionException("MIN_INTERVAL_VIOLATION",
                        String.valueOf(secondsSinceLastTransaction),
                        String.valueOf(minIntervalBetweenTransactionsSeconds));
            }
        }
    }

    /**
     * Валидация валютных операций
     */
    public void validateCurrencyOperation(Currency fromCurrency, Currency toCurrency) {
        if (!allowCrossCurrencyTransactions && !fromCurrency.equals(toCurrency)) {
            throw new InvalidTransactionException("CROSS_CURRENCY_NOT_ALLOWED",
                    fromCurrency.getCode(),
                    toCurrency.getCode());
        }

        // Проверка поддерживаемых валют
        Set<String> supportedCurrencies = Currency.getSupportedCurrencies();
        if (!supportedCurrencies.contains(fromCurrency.getCode())) {
            throw new InvalidTransactionException("UNSUPPORTED_FROM_CURRENCY",
                    fromCurrency.getCode(),
                    "Одна из поддерживаемых: " + supportedCurrencies);
        }

        if (!supportedCurrencies.contains(toCurrency.getCode())) {
            throw new InvalidTransactionException("UNSUPPORTED_TO_CURRENCY",
                    toCurrency.getCode(),
                    "Одна из поддерживаемых: " + supportedCurrencies);
        }
    }

    /**
     * Расчет комиссии за конвертацию валют
     */
    public Money calculateCurrencyConversionFee(Money amount, Currency fromCurrency, Currency toCurrency) {
        if (fromCurrency.equals(toCurrency)) {
            return Money.zero();
        }

        // Простой расчет комиссии как процент от суммы
        BigDecimal feeRate = BigDecimal.valueOf(maxCurrencyConversionFeePercent / 100.0);
        return amount.multiply(feeRate);
    }

    /**
     * Проверка возможности операции администратором
     */
    public void validateAdminOperation(String adminUser, String operation) {
        if (adminUser == null || adminUser.trim().isEmpty()) {
            throw new InvalidTransactionException("ADMIN_USER_REQUIRED", "null", "Имя администратора");
        }

        if (operation == null || operation.trim().isEmpty()) {
            throw new InvalidTransactionException("ADMIN_OPERATION_REQUIRED", "null", "Тип операции");
        }

        // Здесь можно добавить дополнительные проверки прав администратора
        // например, проверку ролей или разрешений
    }

    /**
     * Получение минимальной суммы пополнения
     */
    public Money getMinDepositAmount() {
        return Money.of(minDepositAmount);
    }

    /**
     * Получение максимальной суммы пополнения
     */
    public Money getMaxDepositAmount() {
        return Money.of(maxDepositAmount);
    }

    /**
     * Получение минимальной суммы списания
     */
    public Money getMinWithdrawalAmount() {
        return Money.of(minWithdrawalAmount);
    }

    /**
     * Получение максимальной суммы списания
     */
    public Money getMaxWithdrawalAmount() {
        return Money.of(maxWithdrawalAmount);
    }

    /**
     * Получение дневного лимита пополнений
     */
    public Money getDailyDepositLimit() {
        return Money.of(dailyDepositLimit);
    }

    /**
     * Получение дневного лимита списаний
     */
    public Money getDailyWithdrawalLimit() {
        return Money.of(dailyWithdrawalLimit);
    }

    /**
     * Получение лимита транзакций в день
     */
    public int getDailyTransactionCountLimit() {
        return dailyTransactionCountLimit;
    }

    /**
     * Получение лимита транзакций в минуту
     */
    public int getMaxTransactionsPerMinute() {
        return maxTransactionsPerMinute;
    }

    /**
     * Проверка разрешения операции с балансом (Rate Limiting)
     * ДОБАВЛЕНО: Для защиты от DoS атак
     */
    public boolean isBalanceOperationAllowed(Long userId) {
        UserRateLimitInfo info = userRateLimits.computeIfAbsent(userId, k -> new UserRateLimitInfo());
        LocalDateTime now = LocalDateTime.now();

        // Очистка старых записей
        info.cleanOldOperations(now);

        // Проверка лимита операций в минуту
        if (info.getOperationsInLastMinute(now) >= maxTransactionsPerMinute) {
            return false;
        }

        // Проверка минимального интервала между операциями
        if (info.lastOperationTime != null) {
            long secondsSinceLastOperation = ChronoUnit.SECONDS.between(info.lastOperationTime, now);
            if (secondsSinceLastOperation < minIntervalBetweenTransactionsSeconds) {
                return false;
            }
        }

        // Регистрируем операцию
        info.registerOperation(now);
        return true;
    }

    /**
     * Внутренний класс для отслеживания Rate Limiting по пользователям
     */
    private static class UserRateLimitInfo {
        private LocalDateTime lastOperationTime;
        private final Map<LocalDateTime, Integer> operationsByMinute = new ConcurrentHashMap<>();

        void registerOperation(LocalDateTime time) {
            lastOperationTime = time;
            LocalDateTime minuteKey = time.withSecond(0).withNano(0);
            operationsByMinute.merge(minuteKey, 1, Integer::sum);
        }

        int getOperationsInLastMinute(LocalDateTime now) {
            LocalDateTime currentMinute = now.withSecond(0).withNano(0);
            return operationsByMinute.getOrDefault(currentMinute, 0);
        }

        void cleanOldOperations(LocalDateTime now) {
            LocalDateTime cutoff = now.minusMinutes(2);
            operationsByMinute.entrySet().removeIf(entry -> entry.getKey().isBefore(cutoff));
        }
    }
}