package shit.back.domain.balance.events;

import java.time.Instant;
import java.util.Objects;
import shit.back.domain.balance.valueobjects.TransactionId;
import shit.back.entity.TransactionStatus;

/**
 * Событие завершения транзакции.
 */
public final class TransactionCompletedEvent {
    private final TransactionId transactionId;
    private final Instant timestamp;
    private final TransactionStatus status;

    public TransactionCompletedEvent(TransactionId transactionId, Instant timestamp, TransactionStatus status) {
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
        if (!(o instanceof TransactionCompletedEvent))
            return false;
        TransactionCompletedEvent that = (TransactionCompletedEvent) o;
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
        return "TransactionCompletedEvent{" +
                "transactionId=" + transactionId +
                ", timestamp=" + timestamp +
                ", status=" + status +
                '}';
    }
}