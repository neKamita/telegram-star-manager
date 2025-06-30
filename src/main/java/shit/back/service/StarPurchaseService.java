package shit.back.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shit.back.application.balance.dto.response.SimpleBalanceResponse;
import shit.back.application.balance.service.BalanceApplicationServiceV2;
import shit.back.domain.balance.BalanceAggregate;
import shit.back.domain.balance.exceptions.InsufficientFundsException;
import shit.back.domain.balance.valueobjects.Money;
import shit.back.application.balance.repository.BalanceAggregateRepository;

/**
 * Сервис для прямой покупки звезд из единого баланса
 * 
 * Упрощает сложную DualBalance логику, обеспечивая прямую покупку звезд
 * через единый баланс пользователя.
 * Следует принципам SOLID, DRY, Clean Code, KISS.
 */
@Service
@Transactional
public class StarPurchaseService {

    private static final Logger log = LoggerFactory.getLogger(StarPurchaseService.class);

    private final BalanceApplicationServiceV2 balanceService;
    private final FragmentIntegrationService fragmentIntegrationService;
    private final BalanceAggregateRepository balanceRepository;

    public StarPurchaseService(
            BalanceApplicationServiceV2 balanceService,
            FragmentIntegrationService fragmentIntegrationService,
            BalanceAggregateRepository balanceRepository) {
        this.balanceService = balanceService;
        this.fragmentIntegrationService = fragmentIntegrationService;
        this.balanceRepository = balanceRepository;
    }

    /**
     * Покупка звезд из единого баланса пользователя
     * 
     * @param userId    ID пользователя
     * @param starCount количество звезд
     * @param amount    стоимость покупки
     * @return результат покупки
     */
    public StarPurchaseResult purchaseStars(Long userId, Integer starCount, Money amount) {
        log.info("🌟 Начало покупки звезд: userId={}, stars={}, amount={}",
                userId, starCount, amount.getFormattedAmount());

        try {
            // 1. Валидация входных данных
            validatePurchaseRequest(userId, starCount, amount);

            // 2. Получение BalanceAggregate
            var balanceOptional = balanceRepository.findByUserId(userId);
            if (balanceOptional.isEmpty()) {
                log.warn("⚠️ Баланс не найден для пользователя {}", userId);
                return StarPurchaseResult.failure("BALANCE_NOT_FOUND",
                        "Баланс пользователя не найден");
            }

            BalanceAggregate balance = balanceOptional.get();

            // 3. Проверка достаточности средств
            if (!balance.hasSufficientFunds(amount)) {
                log.warn("💰 Недостаточно средств: требуется {}, доступно {}",
                        amount.getFormattedAmount(),
                        balance.getCurrentBalance().getFormattedAmount());
                return StarPurchaseResult.failure("INSUFFICIENT_FUNDS",
                        String.format("Недостаточно средств. Требуется: %s, доступно: %s",
                                amount.getFormattedAmount(),
                                balance.getCurrentBalance().getFormattedAmount()));
            }

            // 4. Списание с баланса
            String transactionId = generateTransactionId(userId, starCount);
            balance.withdraw(amount,
                    String.format("Покупка %d звезд", starCount),
                    transactionId);

            // 5. Сохранение изменений баланса
            balanceRepository.save(balance);

            // 6. Вызов FragmentIntegrationService
            fragmentIntegrationService.initiateStarPurchase(userId, starCount, amount);

            log.info("✅ Покупка звезд успешно завершена: userId={}, stars={}, transactionId={}",
                    userId, starCount, transactionId);

            return StarPurchaseResult.success(transactionId, starCount, amount);

        } catch (InsufficientFundsException e) {
            log.error("💸 Ошибка недостаточности средств: {}", e.getMessage());
            return StarPurchaseResult.failure("INSUFFICIENT_FUNDS", e.getMessage());

        } catch (Exception e) {
            log.error("❌ Критическая ошибка покупки звезд для userId={}: {}",
                    userId, e.getMessage(), e);
            return StarPurchaseResult.failure("PURCHASE_ERROR",
                    "Произошла ошибка при покупке звезд: " + e.getMessage());
        }
    }

    /**
     * Проверка возможности покупки звезд
     * 
     * @param userId ID пользователя
     * @param amount стоимость покупки
     * @return true если покупка возможна
     */
    public boolean canPurchaseStars(Long userId, Money amount) {
        try {
            var result = balanceService.checkSufficientFunds(userId, amount);
            if (result.isSuccess()) {
                boolean canPurchase = result.getValue();
                log.debug("🔍 Проверка возможности покупки: userId={}, amount={}, result={}",
                        userId, amount.getFormattedAmount(), canPurchase);
                return canPurchase;
            } else {
                log.warn("⚠️ Ошибка при проверке средств: {}", result.getError().getMessage());
                return false;
            }
        } catch (Exception e) {
            log.error("❌ Ошибка проверки возможности покупки: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Валидация запроса на покупку
     */
    private void validatePurchaseRequest(Long userId, Integer starCount, Money amount) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("Некорректный ID пользователя");
        }
        if (starCount == null || starCount <= 0) {
            throw new IllegalArgumentException("Количество звезд должно быть положительным");
        }
        if (amount == null || !amount.isPositive()) {
            throw new IllegalArgumentException("Сумма покупки должна быть положительной");
        }
        if (starCount > 10000) {
            throw new IllegalArgumentException("Превышен лимит количества звезд за одну покупку");
        }
    }

    /**
     * Генерация уникального ID транзакции
     */
    private String generateTransactionId(Long userId, Integer starCount) {
        return String.format("STAR_%d_%d_%d",
                userId, starCount, System.currentTimeMillis());
    }

    /**
     * Результат покупки звезд
     */
    public static class StarPurchaseResult {
        private final boolean success;
        private final String errorCode;
        private final String errorMessage;
        private final String transactionId;
        private final Integer starCount;
        private final Money amount;

        private StarPurchaseResult(boolean success, String errorCode, String errorMessage,
                String transactionId, Integer starCount, Money amount) {
            this.success = success;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            this.transactionId = transactionId;
            this.starCount = starCount;
            this.amount = amount;
        }

        public static StarPurchaseResult success(String transactionId, Integer starCount, Money amount) {
            return new StarPurchaseResult(true, null, null, transactionId, starCount, amount);
        }

        public static StarPurchaseResult failure(String errorCode, String errorMessage) {
            return new StarPurchaseResult(false, errorCode, errorMessage, null, null, null);
        }

        // Геттеры
        public boolean isSuccess() {
            return success;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public String getTransactionId() {
            return transactionId;
        }

        public Integer getStarCount() {
            return starCount;
        }

        public Money getAmount() {
            return amount;
        }

        @Override
        public String toString() {
            return success
                    ? String.format("StarPurchaseResult{success=true, transactionId='%s', stars=%d}",
                            transactionId, starCount)
                    : String.format("StarPurchaseResult{success=false, error='%s: %s'}",
                            errorCode, errorMessage);
        }
    }
}