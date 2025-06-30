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
 * Доменное событие инициации покупки звезд
 * 
 * Генерируется при начале процесса покупки звезд через Fragment API.
 */
public class StarPurchaseInitiatedEvent extends ApplicationEvent {

    private final StarPurchaseId starPurchaseId;
    private final FragmentTransactionId fragmentTransactionId;
    private final Long userId;
    private final Money purchaseAmount;
    private final Integer requestedStars;
    private final String description;
    private final LocalDateTime eventTimestamp;

    /**
     * Конструктор события
     */
    public StarPurchaseInitiatedEvent(Object source, StarPurchaseId starPurchaseId,
            FragmentTransactionId fragmentTransactionId, Long userId,
            Money purchaseAmount, Integer requestedStars,
            String description, LocalDateTime eventTimestamp) {
        super(source);
        this.starPurchaseId = starPurchaseId;
        this.fragmentTransactionId = fragmentTransactionId;
        this.userId = userId;
        this.purchaseAmount = purchaseAmount;
        this.requestedStars = requestedStars;
        this.description = description;
        this.eventTimestamp = eventTimestamp;
    }

    /**
     * Упрощенный конструктор
     */
    public StarPurchaseInitiatedEvent(Object source, StarPurchaseId starPurchaseId, Long userId,
            Money purchaseAmount, Integer requestedStars) {
        this(source, starPurchaseId, null, userId, purchaseAmount, requestedStars,
                "Покупка звезд инициирована", LocalDateTime.now());
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
        return requestedStars != null && requestedStars >= 1000;
    }

    /**
     * Проверка, назначен ли Fragment transaction ID
     */
    public boolean hasFragmentTransactionId() {
        return fragmentTransactionId != null;
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
        if (!(o instanceof StarPurchaseInitiatedEvent))
            return false;
        StarPurchaseInitiatedEvent that = (StarPurchaseInitiatedEvent) o;
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
        return String.format("StarPurchaseInitiatedEvent{starPurchaseId=%s, userId=%d, stars=%d, amount=%s}",
                starPurchaseId.getShortValue(), userId, requestedStars, purchaseAmount.getFormattedAmount());
    }
}