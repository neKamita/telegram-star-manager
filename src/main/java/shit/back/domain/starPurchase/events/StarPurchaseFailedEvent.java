package shit.back.domain.starPurchase.events;

import org.springframework.context.ApplicationEvent;
import shit.back.domain.balance.valueobjects.Money;
import shit.back.domain.starPurchase.valueobjects.FragmentTransactionId;
import shit.back.domain.starPurchase.valueobjects.StarPurchaseId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Доменное событие неудачной покупки звезд
 * 
 * Генерируется при неудачном завершении покупки звезд через Fragment API.
 */
public class StarPurchaseFailedEvent extends ApplicationEvent {

    private final StarPurchaseId starPurchaseId;
    private final FragmentTransactionId fragmentTransactionId;
    private final Long userId;
    private final Money purchaseAmount;
    private final Integer requestedStars;
    private final String failureReason;
    private final String fragmentErrorCode;
    private final boolean isRetryable;
    private final LocalDateTime eventTimestamp;

    /**
     * Конструктор события
     */
    public StarPurchaseFailedEvent(Object source, StarPurchaseId starPurchaseId,
            FragmentTransactionId fragmentTransactionId, Long userId,
            Money purchaseAmount, Integer requestedStars,
            String failureReason, String fragmentErrorCode,
            boolean isRetryable, LocalDateTime eventTimestamp) {
        super(source);
        this.starPurchaseId = starPurchaseId;
        this.fragmentTransactionId = fragmentTransactionId;
        this.userId = userId;
        this.purchaseAmount = purchaseAmount;
        this.requestedStars = requestedStars;
        this.failureReason = failureReason;
        this.fragmentErrorCode = fragmentErrorCode;
        this.isRetryable = isRetryable;
        this.eventTimestamp = eventTimestamp;
    }

    /**
     * Упрощенный конструктор
     */
    public StarPurchaseFailedEvent(Object source, StarPurchaseId starPurchaseId, Long userId,
            Money purchaseAmount, Integer requestedStars,
            String failureReason, String fragmentErrorCode) {
        this(source, starPurchaseId, null, userId, purchaseAmount, requestedStars,
                failureReason, fragmentErrorCode, false, LocalDateTime.now());
    }

    // Геттеры
    public StarPurchaseId getStarPurchaseId() {
        return starPurchaseId;
    }

    public FragmentTransactionId getFragmentTransactionId() {
        return fragmentTransactionId;
    }

    public Long getUserId() {
        return userId;
    }

    public Money getPurchaseAmount() {
        return purchaseAmount;
    }

    public Integer getRequestedStars() {
        return requestedStars;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getFragmentErrorCode() {
        return fragmentErrorCode;
    }

    public boolean isRetryable() {
        return isRetryable;
    }

    public LocalDateTime getEventTimestamp() {
        return eventTimestamp;
    }

    /**
     * Проверка, является ли ошибка связанной с Fragment API
     */
    public boolean isFragmentApiError() {
        return fragmentErrorCode != null && !fragmentErrorCode.trim().isEmpty();
    }

    /**
     * Проверка, является ли ошибка критической
     */
    public boolean isCriticalError() {
        return !isRetryable && isFragmentApiError();
    }

    /**
     * Проверка, является ли ошибка связанной с недостатком средств
     */
    public boolean isInsufficientFundsError() {
        return failureReason != null && failureReason.toLowerCase().contains("insufficient");
    }

    /**
     * Получение стоимости одной звезды
     */
    public Money getPricePerStar() {
        if (requestedStars == null || requestedStars == 0) {
            return Money.zero();
        }
        return Money.of(purchaseAmount.getAmount().divide(BigDecimal.valueOf(requestedStars), 4,
                RoundingMode.HALF_UP));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof StarPurchaseFailedEvent))
            return false;
        StarPurchaseFailedEvent that = (StarPurchaseFailedEvent) o;
        return Objects.equals(starPurchaseId, that.starPurchaseId) &&
                Objects.equals(userId, that.userId) &&
                Objects.equals(eventTimestamp, that.eventTimestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(starPurchaseId, userId, eventTimestamp);
    }

    @Override
    public String toString() {
        return String.format(
                "StarPurchaseFailedEvent{starPurchaseId=%s, userId=%d, stars=%d, amount=%s, reason=%s, retryable=%s}",
                starPurchaseId.getShortValue(), userId, requestedStars,
                purchaseAmount.getFormattedAmount(), failureReason, isRetryable);
    }
}