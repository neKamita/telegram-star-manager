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
 * Доменное событие завершения покупки звезд
 * 
 * Генерируется при успешном завершении покупки звезд через Fragment API.
 */
public class StarPurchaseCompletedEvent extends ApplicationEvent {

    private final StarPurchaseId starPurchaseId;
    private final FragmentTransactionId fragmentTransactionId;
    private final Long userId;
    private final Money purchaseAmount;
    private final Integer purchasedStars;
    private final Money remainingBalance;
    private final String description;
    private final LocalDateTime eventTimestamp;

    /**
     * Конструктор события
     */
    public StarPurchaseCompletedEvent(Object source, StarPurchaseId starPurchaseId,
            FragmentTransactionId fragmentTransactionId, Long userId,
            Money purchaseAmount, Integer purchasedStars, Money remainingBalance,
            String description, LocalDateTime eventTimestamp) {
        super(source);
        this.starPurchaseId = starPurchaseId;
        this.fragmentTransactionId = fragmentTransactionId;
        this.userId = userId;
        this.purchaseAmount = purchaseAmount;
        this.purchasedStars = purchasedStars;
        this.remainingBalance = remainingBalance;
        this.description = description;
        this.eventTimestamp = eventTimestamp;
    }

    /**
     * Упрощенный конструктор
     */
    public StarPurchaseCompletedEvent(Object source, StarPurchaseId starPurchaseId,
            FragmentTransactionId fragmentTransactionId, Long userId,
            Money purchaseAmount, Integer purchasedStars, Money remainingBalance) {
        this(source, starPurchaseId, fragmentTransactionId, userId, purchaseAmount, purchasedStars,
                remainingBalance, "Покупка звезд завершена", LocalDateTime.now());
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

    public Integer getPurchasedStars() {
        return purchasedStars;
    }

    public Money getRemainingBalance() {
        return remainingBalance;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getEventTimestamp() {
        return eventTimestamp;
    }

    /**
     * Проверка, является ли покупка крупной (больше определенного количества звезд)
     */
    public boolean isLargePurchase() {
        return purchasedStars != null && purchasedStars >= 1000;
    }

    /**
     * Получение стоимости одной звезды
     */
    public Money getPricePerStar() {
        if (purchasedStars == null || purchasedStars == 0) {
            return Money.zero();
        }
        return Money.of(purchaseAmount.getAmount().divide(BigDecimal.valueOf(purchasedStars), 4,
                RoundingMode.HALF_UP));
    }

    /**
     * Проверка, является ли покупка успешной
     */
    public boolean isSuccessful() {
        return purchasedStars != null && purchasedStars > 0;
    }

    /**
     * Проверка критичности остатка баланса
     */
    public boolean isLowBalanceAfterPurchase() {
        return remainingBalance.isLessThan(Money.of("100.00"));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof StarPurchaseCompletedEvent))
            return false;
        StarPurchaseCompletedEvent that = (StarPurchaseCompletedEvent) o;
        return Objects.equals(starPurchaseId, that.starPurchaseId) &&
                Objects.equals(fragmentTransactionId, that.fragmentTransactionId) &&
                Objects.equals(userId, that.userId) &&
                Objects.equals(eventTimestamp, that.eventTimestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(starPurchaseId, fragmentTransactionId, userId, eventTimestamp);
    }

    @Override
    public String toString() {
        return String.format(
                "StarPurchaseCompletedEvent{starPurchaseId=%s, fragmentTxId=%s, userId=%d, stars=%d, amount=%s}",
                starPurchaseId.getShortValue(),
                fragmentTransactionId != null ? fragmentTransactionId.getShortValue() : "null",
                userId, purchasedStars, purchaseAmount.getFormattedAmount());
    }
}