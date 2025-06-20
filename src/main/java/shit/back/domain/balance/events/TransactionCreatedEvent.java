package shit.back.domain.balance.events;

import java.time.Instant;
import java.util.Objects;
import shit.back.domain.balance.valueobjects.TransactionId;
import shit.back.entity.TransactionStatus;

/**
 * Событие создания транзакции.
 */
public final class TransactionCreatedEvent {
    private final TransactionId transactionId;
    private final Instant timestamp;
    private final TransactionStatus status;

    public TransactionCreatedEvent(TransactionId transactionId, Instant timestamp, TransactionStatus status) {
        this.transactionId = transactionId;
        this.timestamp = timestamp;
        this.status = status;
    }

    public TransactionId getTransactionId() {
        return transactionId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof TransactionCreatedEvent))
            return false;
        TransactionCreatedEvent that = (TransactionCreatedEvent) o;
        return Objects.equals(transactionId, that.transactionId)
                && Objects.equals(timestamp, that.timestamp)
                && status == that.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionId, timestamp, status);
    }

    @Override
    public String toString() {
        return "TransactionCreatedEvent{" +
                "transactionId=" + transactionId +
                ", timestamp=" + timestamp +
                ", status=" + status +
                '}';
    }
}