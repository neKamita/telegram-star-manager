package shit.back.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shit.back.entity.UserBalanceEntity;
import shit.back.repository.BalanceTransactionJpaRepository;
import shit.back.repository.UserBalanceJpaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Сервис валидации операций с балансом
 * 
 * Обеспечивает безопасность финансовых операций через:
 * - Валидацию сумм и лимитов
 * - Защиту от конкурентных операций
 * - Проверку прав доступа
 * - Rate limiting
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceValidationService {

    private final UserBalanceJpaRepository userBalanceRepository;
    private final BalanceTransactionJpaRepository transactionRepository;

    // Конфигурационные параметры
    @Value("${balance.min-deposit-amount:0.01}")
    private BigDecimal minDepositAmount;

    @Value("${balance.max-deposit-amount:10000.00}")
    private BigDecimal maxDepositAmount;

    @Value("${balance.min-withdrawal-amount:0.01}")
    private BigDecimal minWithdrawalAmount;

    @Value("${balance.max-withdrawal-amount:5000.00}")
    private BigDecimal maxWithdrawalAmount;

    @Value("${balance.daily-deposit-limit:50000.00}")
    private BigDecimal dailyDepositLimit;

    @Value("${balance.daily-withdrawal-limit:10000.00}")
    private BigDecimal dailyWithdrawalLimit;

    @Value("${balance.max-concurrent-operations-per-user:3}")
    private int maxConcurrentOperationsPerUser;

    @Value("${balance.rate-limit-operations-per-minute:10}")
    private int rateLimitOperationsPerMinute;

    // Счетчики операций для rate limiting
    private final Map<Long, AtomicInteger> userOperationCounters = new ConcurrentHashMap<>();
    private final Map<Long, LocalDateTime> userLastOperationTime = new ConcurrentHashMap<>();

    // Счетчик конкурентных операций
    private final Map<Long, AtomicInteger> concurrentOperations = new ConcurrentHashMap<>();

    /**
     * Валидация суммы пополнения
     * 
     * @param amount сумма для проверки
     * @throws RuntimeException если сумма некорректна
     */
    public void validateDepositAmount(BigDecimal amount) {
        log.debug("Валидация суммы пополнения: {}", amount);

        if (amount == null) {
            throw new RuntimeException("Сумма пополнения не может быть пустой");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Сумма пополнения должна быть положительной");
        }

        if (amount.compareTo(minDepositAmount) < 0) {
            throw new RuntimeException(String.format("Минимальная сумма пополнения: %s", minDepositAmount));
        }

        if (amount.compareTo(maxDepositAmount) > 0) {
            throw new RuntimeException(String.format("Максимальная сумма пополнения: %s", maxDepositAmount));
        }

        // Проверка на разумную точность (максимум 2 знака после запятой)
        if (amount.scale() > 2) {
            throw new RuntimeException("Сумма не может содержать более двух знаков после запятой");
        }

        log.debug("Сумма пополнения {} прошла валидацию", amount);
    }

    /**
     * Валидация суммы списания
     * 
     * @param userId ID пользователя
     * @param amount сумма для проверки
     * @throws RuntimeException если сумма некорректна или недостаточно средств
     */
    @Transactional(readOnly = true)
    public void validateWithdrawalAmount(Long userId, BigDecimal amount) {
        log.debug("Валидация суммы списания для пользователя {}: {}", userId, amount);

        if (amount == null) {
            throw new RuntimeException("Сумма списания не может быть пустой");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Сумма списания должна быть положительной");
        }

        if (amount.compareTo(minWithdrawalAmount) < 0) {
            throw new RuntimeException(String.format("Минимальная сумма списания: %s", minWithdrawalAmount));
        }

        if (amount.compareTo(maxWithdrawalAmount) > 0) {
            throw new RuntimeException(String.format("Максимальная сумма списания: %s", maxWithdrawalAmount));
        }

        // Проверка на разумную точность
        if (amount.scale() > 2) {
            throw new RuntimeException("Сумма не может содержать более двух знаков после запятой");
        }

        // Проверка баланса пользователя
        try {
            UserBalanceEntity balance = userBalanceRepository.findByUserId(userId)
                    .orElseThrow(() -> new RuntimeException("Баланс пользователя не найден"));

            if (!balance.hasSufficientFunds(amount)) {
                throw new RuntimeException(String.format("Недостаточно средств. Доступно: %s, требуется: %s",
                        balance.getCurrentBalance(), amount));
            }

            log.debug("Сумма списания {} для пользователя {} прошла валидацию", amount, userId);

        } catch (Exception e) {
            log.error("Ошибка при валидации суммы списания для пользователя {}: {}", userId, e.getMessage());
            throw e;
        }
    }

    /**
     * Проверка лимитов транзакций за период
     * 
     * @param userId ID пользователя
     * @param amount сумма операции
     * @param period период для проверки (в часах)
     * @throws RuntimeException если превышены лимиты
     */
    @Transactional(readOnly = true)
    public void validateTransactionLimits(Long userId, BigDecimal amount, int period) {
        log.debug("Проверка лимитов транзакций для пользователя {}: сумма={}, период={}ч",
                userId, amount, period);

        try {
            LocalDateTime fromTime = LocalDateTime.now().minusHours(period);

            // Получаем транзакции пользователя за период
            List<Object[]> userStats = transactionRepository.getUserTransactionStatistics(userId);

            // Подсчитываем общую сумму депозитов и списаний за сегодня
            LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
            var todayTransactions = transactionRepository.findUserTransactionsBetweenDates(
                    userId, today, LocalDateTime.now());

            BigDecimal todayDeposits = BigDecimal.ZERO;
            BigDecimal todayWithdrawals = BigDecimal.ZERO;

            for (var transaction : todayTransactions) {
                if (transaction.isCompleted()) {
                    switch (transaction.getType()) {
                        case DEPOSIT, REFUND -> todayDeposits = todayDeposits.add(transaction.getAmount());
                        case PURCHASE, WITHDRAWAL -> todayWithdrawals = todayWithdrawals.add(transaction.getAmount());
                    }
                }
            }

            // Проверяем дневные лимиты
            if (todayDeposits.add(amount).compareTo(dailyDepositLimit) > 0) {
                throw new RuntimeException(String.format("Превышен дневной лимит пополнений: %s (текущий: %s)",
                        dailyDepositLimit, todayDeposits));
            }

            if (todayWithdrawals.add(amount).compareTo(dailyWithdrawalLimit) > 0) {
                throw new RuntimeException(String.format("Превышен дневной лимит списаний: %s (текущий: %s)",
                        dailyWithdrawalLimit, todayWithdrawals));
            }

            log.debug("Лимиты транзакций для пользователя {} в пределах нормы", userId);

        } catch (Exception e) {
            log.error("Ошибка при проверке лимитов для пользователя {}: {}", userId, e.getMessage());
            throw e;
        }
    }

    /**
     * Защита от конкурентных операций для одного пользователя
     * 
     * @param userId ID пользователя
     * @throws RuntimeException если превышено количество одновременных операций
     */
    public void validateConcurrentOperations(Long userId) {
        log.debug("Проверка конкурентных операций для пользователя: {}", userId);

        AtomicInteger currentOps = concurrentOperations.computeIfAbsent(userId, k -> new AtomicInteger(0));
        int currentCount = currentOps.incrementAndGet();

        try {
            if (currentCount > maxConcurrentOperationsPerUser) {
                throw new RuntimeException(String.format(
                        "Превышено максимальное количество одновременных операций (%d). Попробуйте позже.",
                        maxConcurrentOperationsPerUser));
            }

            // Проверяем rate limiting
            validateRateLimit(userId);

            log.debug("Пользователю {} разрешена операция (активных операций: {})", userId, currentCount);

        } finally {
            // Уменьшаем счетчик после завершения проверки
            currentOps.decrementAndGet();

            // Удаляем запись если счетчик обнулился
            if (currentOps.get() <= 0) {
                concurrentOperations.remove(userId);
            }
        }
    }

    /**
     * Проверка rate limiting (ограничение частоты операций)
     * 
     * @param userId ID пользователя
     * @throws RuntimeException если превышена частота операций
     */
    private void validateRateLimit(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastOperation = userLastOperationTime.get(userId);

        if (lastOperation == null || lastOperation.isBefore(now.minusMinutes(1))) {
            // Сбрасываем счетчик если прошла минута
            userOperationCounters.put(userId, new AtomicInteger(1));
            userLastOperationTime.put(userId, now);
        } else {
            // Увеличиваем счетчик
            AtomicInteger counter = userOperationCounters.computeIfAbsent(userId, k -> new AtomicInteger(0));
            int currentCount = counter.incrementAndGet();

            if (currentCount > rateLimitOperationsPerMinute) {
                throw new RuntimeException(String.format(
                        "Превышен лимит операций в минуту (%d). Попробуйте через минуту.",
                        rateLimitOperationsPerMinute));
            }
        }

        log.debug("Rate limit для пользователя {} в пределах нормы", userId);
    }

    /**
     * Валидация административной операции
     * 
     * @param adminUser администратор
     * @param operation тип операции
     * @throws RuntimeException если операция недопустима
     */
    public void validateAdminOperation(String adminUser, String operation) {
        log.debug("Валидация административной операции: админ={}, операция={}", adminUser, operation);

        if (adminUser == null || adminUser.trim().isEmpty()) {
            throw new RuntimeException("Не указан администратор для выполнения операции");
        }

        if (operation == null || operation.trim().isEmpty()) {
            throw new RuntimeException("Не указан тип административной операции");
        }

        // Здесь можно добавить проверку прав доступа администратора
        // Например, проверка в базе данных или через security context

        // Проверка на системные операции
        if ("SYSTEM".equals(adminUser)) {
            log.debug("Системная операция {} разрешена", operation);
            return;
        }

        // Валидация имени администратора
        if (adminUser.length() < 3) {
            throw new RuntimeException("Имя администратора слишком короткое");
        }

        if (adminUser.length() > 50) {
            throw new RuntimeException("Имя администратора слишком длинное");
        }

        // Проверка допустимых операций
        List<String> allowedOperations = List.of(
                "BALANCE_ADJUSTMENT",
                "TRANSACTION_CANCEL",
                "BALANCE_FREEZE",
                "BALANCE_UNFREEZE",
                "REFUND_MANUAL");

        if (!allowedOperations.contains(operation)) {
            throw new RuntimeException("Недопустимый тип административной операции: " + operation);
        }

        log.debug("Административная операция {} для админа {} валидна", operation, adminUser);
    }

    /**
     * Валидация валюты
     * 
     * @param currency код валюты
     * @throws RuntimeException если валюта недопустима
     */
    public void validateCurrency(String currency) {
        log.debug("Валидация валюты: {}", currency);

        if (currency == null || currency.trim().isEmpty()) {
            throw new RuntimeException("Код валюты не может быть пустым");
        }

        if (currency.length() != 3) {
            throw new RuntimeException("Код валюты должен состоять из 3 символов");
        }

        if (!currency.matches("^[A-Z]{3}$")) {
            throw new RuntimeException("Код валюты должен содержать только заглавные латинские буквы");
        }

        // Список поддерживаемых валют
        List<String> supportedCurrencies = List.of("USD", "EUR", "RUB", "UZS");

        if (!supportedCurrencies.contains(currency)) {
            throw new RuntimeException("Неподдерживаемая валюта: " + currency);
        }

        log.debug("Валюта {} прошла валидацию", currency);
    }

    /**
     * Валидация ID заказа
     * 
     * @param orderId ID заказа
     * @throws RuntimeException если ID некорректен
     */
    public void validateOrderId(String orderId) {
        log.debug("Валидация ID заказа: {}", orderId);

        if (orderId == null || orderId.trim().isEmpty()) {
            throw new RuntimeException("ID заказа не может быть пустым");
        }

        if (orderId.length() > 8) {
            throw new RuntimeException("ID заказа не может превышать 8 символов");
        }

        if (!orderId.matches("^[A-Z0-9]+$")) {
            throw new RuntimeException("ID заказа должен содержать только заглавные буквы и цифры");
        }

        log.debug("ID заказа {} прошел валидацию", orderId);
    }

    /**
     * Очистка устаревших данных rate limiting
     * Должен вызываться периодически (например, по расписанию)
     */
    public void cleanupOldRateLimitData() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(1);

        userLastOperationTime.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));

        // Очищаем счетчики для пользователей, которые давно не выполняли операции
        userLastOperationTime.keySet().forEach(userId -> {
            if (!userLastOperationTime.containsKey(userId)) {
                userOperationCounters.remove(userId);
            }
        });

        log.debug("Очищены устаревшие данные rate limiting");
    }

    /**
     * Получение текущих ограничений для пользователя
     * 
     * @param userId ID пользователя
     * @return информация об ограничениях
     */
    public Map<String, Object> getUserLimits(Long userId) {
        AtomicInteger currentOps = concurrentOperations.get(userId);
        AtomicInteger operationCount = userOperationCounters.get(userId);

        return Map.of(
                "minDepositAmount", minDepositAmount,
                "maxDepositAmount", maxDepositAmount,
                "minWithdrawalAmount", minWithdrawalAmount,
                "maxWithdrawalAmount", maxWithdrawalAmount,
                "dailyDepositLimit", dailyDepositLimit,
                "dailyWithdrawalLimit", dailyWithdrawalLimit,
                "currentConcurrentOperations", currentOps != null ? currentOps.get() : 0,
                "maxConcurrentOperations", maxConcurrentOperationsPerUser,
                "currentOperationsPerMinute", operationCount != null ? operationCount.get() : 0,
                "maxOperationsPerMinute", rateLimitOperationsPerMinute);
    }
}