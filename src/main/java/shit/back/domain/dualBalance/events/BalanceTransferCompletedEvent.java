package shit.back.domain.dualBalance.events;

import org.springframework.context.ApplicationEvent;
import shit.back.domain.balance.valueobjects.Money;
import shit.back.domain.dualBalance.valueobjects.BalanceType;
import shit.back.domain.dualBalance.valueobjects.DualBalanceId;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Доменное событие завершения трансфера между балансами
 * 
 * Генерируется при успешном завершении процесса трансфера средств
 * между bank и main балансами.
 */
public class BalanceTransferCompletedEvent extends ApplicationEvent {

    private final DualBalanceId dualBalanceId;
    private final Long userId;
    private final BalanceType fromType;
    private final BalanceType toType;
    private final Money transferAmount;
    private final Money fromBalanceAfter;
    private final Money toBalanceAfter;
    private final String transferId;
    private final String description;
    private final LocalDateTime eventTimestamp;

    /**
     * Конструктор события
     */
    public BalanceTransferCompletedEvent(Object source, DualBalanceId dualBalanceId, Long userId,
            BalanceType fromType, BalanceType toType, Money transferAmount,
            Money fromBalanceAfter, Money toBalanceAfter,
            String transferId, String description, LocalDateTime eventTimestamp) {
        super(source);
        this.dualBalanceId = dualBalanceId;
        this.userId = userId;
        this.fromType = fromType;
        this.toType = toType;
        this.transferAmount = transferAmount;
        this.fromBalanceAfter = fromBalanceAfter;
        this.toBalanceAfter = toBalanceAfter;
        this.transferId = transferId;
        this.description = description;
        this.eventTimestamp = eventTimestamp;
    }

    /**
     * Упрощенный конструктор
     */
    public BalanceTransferCompletedEvent(Object source, DualBalanceId dualBalanceId, Long userId,
            BalanceType fromType, BalanceType toType, Money transferAmount,
            Money fromBalanceAfter, Money toBalanceAfter, String transferId) {
        this(source, dualBalanceId, userId, fromType, toType, transferAmount, fromBalanceAfter, toBalanceAfter,
                transferId, "Трансфер завершен", LocalDateTime.now());
    }

    // Геттеры
    public DualBalanceId getDualBalanceId() {
        return dualBalanceId;
    }

    public Long getUserId() {
        return userId;
    }

    public BalanceType getFromType() {
        return fromType;
    }

    public BalanceType getToType() {
        return toType;
    }

    public Money getTransferAmount() {
        return transferAmount;
    }

    public Money getFromBalanceAfter() {
        return fromBalanceAfter;
    }

    public Money getToBalanceAfter() {
        return toBalanceAfter;
    }

    public String getTransferId() {
        return transferId;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getEventTimestamp() {
        return eventTimestamp;
    }

    /**
     * Проверка, является ли трансфер из bank в main
     */
    public boolean isBankToMainTransfer() {
        return fromType == BalanceType.BANK && toType == BalanceType.MAIN;
    }

    /**
     * Проверка, является ли трансфер из main в bank
     */
    public boolean isMainToBankTransfer() {
        return fromType == BalanceType.MAIN && toType == BalanceType.BANK;
    }

    /**
     * Проверка, является ли трансфер крупным (больше определенной суммы)
     */
    public boolean isLargeTransfer() {
        return transferAmount.isGreaterThan(Money.of("1000.00"));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof BalanceTransferCompletedEvent))
            return false;
        BalanceTransferCompletedEvent that = (BalanceTransferCompletedEvent) o;
        return Objects.equals(dualBalanceId, that.dualBalanceId) &&
                Objects.equals(userId, that.userId) &&
                Objects.equals(transferId, that.transferId) &&
                Objects.equals(eventTimestamp, that.eventTimestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dualBalanceId, userId, transferId, eventTimestamp);
    }

    @Override
    public String toString() {
        return String.format(
                "BalanceTransferCompletedEvent{dualBalanceId=%s, userId=%d, %s->%s, amount=%s, transferId=%s}",
                dualBalanceId, userId, fromType.getCode(), toType.getCode(),
                transferAmount.getFormattedAmount(), transferId);
    }
}