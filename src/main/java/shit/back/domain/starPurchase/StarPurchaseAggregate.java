package shit.back.domain.starPurchase;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.AbstractAggregateRoot;
import shit.back.domain.balance.valueobjects.Currency;
import shit.back.domain.balance.valueobjects.Money;
import shit.back.domain.starPurchase.events.StarPurchaseCompletedEvent;
import shit.back.domain.starPurchase.events.StarPurchaseFailedEvent;
import shit.back.domain.starPurchase.events.StarPurchaseInitiatedEvent;
import shit.back.domain.starPurchase.exceptions.StarPurchaseFailedException;
import shit.back.domain.starPurchase.valueobjects.FragmentTransactionId;
import shit.back.domain.starPurchase.valueobjects.StarPurchaseId;
import shit.back.entity.TransactionStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Агрегат покупки звезд через Fragment API
 * 
 * Управляет процессом покупки звезд через Telegram Fragment.
 * Связан с DualBalanceAggregate для списания с mainBalance.
 * Отслеживает статусы покупки звезд и генерирует события инициации, завершения,
 * ошибок покупки.
 */
@Entity
@Table(name = "star_purchases", indexes = {
        @Index(name = "idx_star_purchase_id", columnList = "star_purchase_id", unique = true),
        @Index(name = "idx_star_purchase_user_id", columnList = "user_id"),
        @Index(name = "idx_star_purchase_fragment_tx", columnList = "fragment_transaction_id"),
        @Index(name = "idx_star_purchase_status", columnList = "status"),
        @Index(name = "idx_star_purchase_created_at", columnList = "created_at"),
        @Index(name = "idx_star_purchase_dual_balance", columnList = "dual_balance_id")
})
public class StarPurchaseAggregate extends AbstractAggregateRoot<StarPurchaseAggregate> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "star_purchase_id", length = 36, unique = true))
    private StarPurchaseId starPurchaseId;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "dual_balance_id")
    private Long dualBalanceId;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "fragment_transaction_id", length = 36))
    private FragmentTransactionId fragmentTransactionId;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "purchase_amount", precision = 12, scale = 2))
    private Money purchaseAmount;

    @Column(name = "requested_stars", nullable = false)
    @NotNull
    private Integer requestedStars;

    @Column(name = "actual_stars_received")
    private Integer actualStarsReceived;

    @Embedded
    @AttributeOverride(name = "code", column = @Column(name = "currency", length = 3))
    private Currency currency;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "main_balance_before", precision = 12, scale = 2))
    private Money mainBalanceBefore;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "main_balance_after", precision = 12, scale = 2))
    private Money mainBalanceAfter;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @NotNull
    private TransactionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "initiated_at")
    private LocalDateTime initiatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "fragment_response", columnDefinition = "TEXT")
    private String fragmentResponse;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "fragment_error_code", length = 100)
    private String fragmentErrorCode;

    @Version
    @Column(name = "version")
    private Long version;

    @Transient
    private static final int DEFAULT_TIMEOUT_MINUTES = 30;

    /**
     * Конструктор по умолчанию для JPA
     */
    protected StarPurchaseAggregate() {
    }

    /**
     * Конструктор для создания новой покупки звезд
     */
    public StarPurchaseAggregate(Long userId, Long dualBalanceId, Money purchaseAmount,
            Integer requestedStars, Currency currency, Money mainBalanceBefore, String description) {
        if (userId == null) {
            throw new StarPurchaseFailedException(null, "USER_ID_REQUIRED",
                    new IllegalArgumentException("ID пользователя не может быть null"));
        }
        if (purchaseAmount == null || !purchaseAmount.isPositive()) {
            throw new StarPurchaseFailedException(null, "INVALID_PURCHASE_AMOUNT",
                    new IllegalArgumentException("Сумма покупки должна быть положительной"));
        }
        if (requestedStars == null || requestedStars <= 0) {
            throw new StarPurchaseFailedException(null, "INVALID_STARS_COUNT",
                    new IllegalArgumentException("Количество звезд должно быть положительным"));
        }
        if (!isValidStarsAmount(requestedStars)) {
            throw new StarPurchaseFailedException(null, "INVALID_STARS_PACKAGE",
                    new IllegalArgumentException("Недопустимое количество звезд для покупки"));
        }

        this.starPurchaseId = StarPurchaseId.generate();
        this.userId = userId;
        this.dualBalanceId = dualBalanceId;
        this.purchaseAmount = purchaseAmount;
        this.requestedStars = requestedStars;
        this.currency = currency != null ? currency : Currency.defaultCurrency();
        this.mainBalanceBefore = mainBalanceBefore;
        this.description = description != null ? description : "Покупка звезд через Fragment API";
        this.status = TransactionStatus.PENDING;
        this.createdAt = LocalDateTime.now();

        // Генерируем событие инициации покупки
        registerEvent(new StarPurchaseInitiatedEvent(
                this, starPurchaseId, userId, purchaseAmount, requestedStars));
    }

    /**
     * Инициация покупки через Fragment API
     */
    public void initiate(FragmentTransactionId fragmentTransactionId) {
        validateCanChangeStatus();

        this.fragmentTransactionId = fragmentTransactionId;
        this.initiatedAt = LocalDateTime.now();
        // Статус остается PENDING до получения ответа от Fragment
    }

    /**
     * Завершение успешной покупки
     */
    public void complete(Integer actualStarsReceived, Money mainBalanceAfter, String fragmentResponse) {
        validateCanChangeStatus();

        if (this.status == TransactionStatus.COMPLETED) {
            return; // Уже завершена
        }

        this.actualStarsReceived = actualStarsReceived;
        this.mainBalanceAfter = mainBalanceAfter;
        this.fragmentResponse = fragmentResponse;
        this.status = TransactionStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();

        // Генерируем событие завершения покупки
        registerEvent(new StarPurchaseCompletedEvent(
                this, starPurchaseId, fragmentTransactionId, userId,
                purchaseAmount, actualStarsReceived, mainBalanceAfter));
    }

    /**
     * Отмена покупки
     */
    public void cancel(String reason) {
        validateCanChangeStatus();

        if (this.status == TransactionStatus.CANCELLED) {
            return; // Уже отменена
        }

        this.status = TransactionStatus.CANCELLED;
        this.completedAt = LocalDateTime.now();
        this.failureReason = reason;

        updateDescription("[ОТМЕНЕНО: " + reason + "]");
    }

    /**
     * Пометка покупки как неудачной
     */
    public void fail(String reason, String fragmentErrorCode) {
        validateCanChangeStatus();

        if (this.status == TransactionStatus.FAILED) {
            return; // Уже помечена как неудачная
        }

        this.status = TransactionStatus.FAILED;
        this.completedAt = LocalDateTime.now();
        this.failureReason = reason;
        this.fragmentErrorCode = fragmentErrorCode;

        updateDescription("[ОШИБКА: " + reason + "]");

        // Определяем, можно ли повторить попытку
        boolean isRetryable = fragmentErrorCode != null &&
                (fragmentErrorCode.equals("TEMPORARY_ERROR") ||
                        fragmentErrorCode.equals("RATE_LIMITED") ||
                        fragmentErrorCode.equals("SERVICE_UNAVAILABLE"));

        // Генерируем событие неудачной покупки
        registerEvent(new StarPurchaseFailedEvent(
                this, starPurchaseId, fragmentTransactionId, userId,
                purchaseAmount, requestedStars, reason, fragmentErrorCode,
                isRetryable, LocalDateTime.now()));
    }

    /**
     * Проверка истечения времени ожидания покупки
     */
    public boolean isTimedOut() {
        return isTimedOut(DEFAULT_TIMEOUT_MINUTES);
    }

    /**
     * Проверка истечения времени ожидания с заданным таймаутом
     */
    public boolean isTimedOut(int timeoutMinutes) {
        if (this.status != TransactionStatus.PENDING) {
            return false; // Только pending покупки могут истечь
        }

        LocalDateTime timeoutThreshold = this.createdAt.plus(timeoutMinutes, ChronoUnit.MINUTES);
        return LocalDateTime.now().isAfter(timeoutThreshold);
    }

    /**
     * Автоматическая отмена по таймауту
     */
    public void timeoutCancel() {
        if (!isTimedOut()) {
            throw new StarPurchaseFailedException(starPurchaseId, "PURCHASE_NOT_TIMED_OUT",
                    new IllegalStateException("Покупка звезд еще не истекла"));
        }

        cancel("Автоматическая отмена по таймауту");
    }

    /**
     * Проверка возможности возврата средств
     */
    public boolean canRefund() {
        return this.status == TransactionStatus.FAILED || this.status == TransactionStatus.CANCELLED;
    }

    /**
     * Получение стоимости одной звезды
     */
    public Money getPricePerStar() {
        if (requestedStars == 0) {
            return Money.zero();
        }
        return Money.of(purchaseAmount.getAmount().divide(BigDecimal.valueOf(requestedStars), 4,
                RoundingMode.HALF_UP));
    }

    /**
     * Получение эффективности покупки (актуальные звезды / запрошенные звезды)
     */
    public double getPurchaseEfficiency() {
        if (requestedStars == 0 || actualStarsReceived == null) {
            return 0.0;
        }
        return (double) actualStarsReceived / requestedStars;
    }

    /**
     * Получение времени выполнения покупки
     */
    public Long getProcessingTimeSeconds() {
        if (completedAt == null) {
            return null;
        }
        return ChronoUnit.SECONDS.between(createdAt, completedAt);
    }

    /**
     * Проверка валидности количества звезд для покупки
     */
    private static boolean isValidStarsAmount(Integer stars) {
        // Стандартные пакеты звезд Telegram
        int[] validPackages = { 1, 3, 5, 10, 25, 50, 100, 250, 500, 1000, 2500 };
        for (int validPackage : validPackages) {
            if (stars.equals(validPackage)) {
                return true;
            }
        }
        return false;
    }

    // Проверочные методы

    public boolean isCompleted() {
        return status == TransactionStatus.COMPLETED;
    }

    public boolean isPending() {
        return status == TransactionStatus.PENDING;
    }

    public boolean isCancelled() {
        return status == TransactionStatus.CANCELLED;
    }

    public boolean isFailed() {
        return status == TransactionStatus.FAILED;
    }

    public boolean isInitiated() {
        return fragmentTransactionId != null && initiatedAt != null;
    }

    public boolean isLargePurchase() {
        return requestedStars >= 1000;
    }

    /**
     * Проверка наличия Fragment транзакции
     */
    public boolean hasFragmentTransaction() {
        return fragmentTransactionId != null;
    }

    /**
     * Получение сообщения об ошибке для UI
     */
    public String getErrorMessage() {
        if (failureReason != null) {
            return failureReason;
        }
        if (fragmentErrorCode != null) {
            return "Ошибка Fragment API: " + fragmentErrorCode;
        }
        if (status == TransactionStatus.CANCELLED) {
            return "Покупка отменена";
        }
        if (status == TransactionStatus.FAILED) {
            return "Покупка не удалась";
        }
        return null;
    }

    // Приватные методы

    private void validateCanChangeStatus() {
        if (this.status == TransactionStatus.COMPLETED) {
            throw new StarPurchaseFailedException(starPurchaseId, "PURCHASE_ALREADY_COMPLETED",
                    new IllegalStateException("Покупка звезд уже завершена"));
        }
        if (this.status == TransactionStatus.CANCELLED) {
            throw new StarPurchaseFailedException(starPurchaseId, "PURCHASE_ALREADY_CANCELLED",
                    new IllegalStateException("Покупка звезд уже отменена"));
        }
        if (this.status == TransactionStatus.FAILED) {
            throw new StarPurchaseFailedException(starPurchaseId, "PURCHASE_ALREADY_FAILED",
                    new IllegalStateException("Покупка звезд уже помечена как неудачная"));
        }
    }

    private void updateDescription(String suffix) {
        if (this.description == null) {
            this.description = suffix;
        } else {
            this.description = this.description + " " + suffix;
        }
    }

    // Геттеры

    public Long getId() {
        return id;
    }

    public StarPurchaseId getStarPurchaseId() {
        return starPurchaseId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getDualBalanceId() {
        return dualBalanceId;
    }

    public FragmentTransactionId getFragmentTransactionId() {
        return fragmentTransactionId;
    }

    public Money getPurchaseAmount() {
        return purchaseAmount;
    }

    public Integer getRequestedStars() {
        return requestedStars;
    }

    public Integer getActualStarsReceived() {
        return actualStarsReceived;
    }

    public Currency getCurrency() {
        return currency;
    }

    public Money getMainBalanceBefore() {
        return mainBalanceBefore;
    }

    public Money getMainBalanceAfter() {
        return mainBalanceAfter;
    }

    public String getDescription() {
        return description;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getInitiatedAt() {
        return initiatedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public String getFragmentResponse() {
        return fragmentResponse;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getFragmentErrorCode() {
        return fragmentErrorCode;
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        StarPurchaseAggregate that = (StarPurchaseAggregate) o;
        return Objects.equals(id, that.id) && Objects.equals(starPurchaseId, that.starPurchaseId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, starPurchaseId);
    }

    @Override
    public String toString() {
        return String.format(
                "StarPurchaseAggregate{id=%d, starPurchaseId=%s, userId=%d, stars=%d, amount=%s, status=%s}",
                id,
                starPurchaseId != null ? starPurchaseId.getShortValue() : "null",
                userId, requestedStars,
                purchaseAmount != null ? purchaseAmount.getFormattedAmount() : "null",
                status);
    }
}