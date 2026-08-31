package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxOperation;
import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxStatus;
import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "billing_outbox_commands", uniqueConstraints =
        @UniqueConstraint(name = "uk_billing_outbox_idempotency", columnNames = "idempotency_key"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingOutboxCommand extends BaseEntity {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private BillingOutboxOperation operation;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 160)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BillingOutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Version
    @Column(name = "row_version", nullable = false)
    private long version;

    public static BillingOutboxCommand pending(
            BillingOutboxOperation operation,
            UUID aggregateId,
            String idempotencyKey,
            Instant now
    ) {
        BillingOutboxCommand command = new BillingOutboxCommand();
        command.operation = Objects.requireNonNull(operation);
        command.aggregateId = Objects.requireNonNull(aggregateId);
        command.idempotencyKey = requireText(idempotencyKey);
        command.status = BillingOutboxStatus.PENDING;
        command.nextAttemptAt = Objects.requireNonNull(now);
        return command;
    }

    public void claim(Instant now) {
        if (status != BillingOutboxStatus.PENDING || nextAttemptAt.isAfter(now)) {
            throw new IllegalStateException("Outbox command is not ready to claim");
        }
        status = BillingOutboxStatus.PROCESSING;
        claimedAt = now;
        attemptCount++;
    }

    public void processed(Instant now) {
        requireProcessing();
        status = BillingOutboxStatus.PROCESSED;
        processedAt = now;
        claimedAt = null;
        lastError = null;
    }

    public void retry(String error, Instant nextAttemptAt) {
        requireProcessing();
        status = BillingOutboxStatus.PENDING;
        claimedAt = null;
        lastError = safeError(error);
        this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt);
    }

    public void fail(String error, Instant now) {
        requireProcessing();
        status = BillingOutboxStatus.FAILED;
        claimedAt = null;
        processedAt = now;
        lastError = safeError(error);
    }

    public void cancel(String reason, Instant now) {
        if (status != BillingOutboxStatus.PENDING) {
            throw new IllegalStateException("Only a pending outbox command may be cancelled");
        }
        status = BillingOutboxStatus.CANCELLED;
        claimedAt = null;
        processedAt = Objects.requireNonNull(now, "Command cancellation time is required");
        lastError = safeError(reason);
    }

    public void recoverStaleClaim(String error, Instant nextAttemptAt) {
        if (status != BillingOutboxStatus.PROCESSING) return;
        status = BillingOutboxStatus.PENDING;
        claimedAt = null;
        lastError = safeError(error);
        this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt);
    }

    public void reconciled(Instant now) {
        if (status == BillingOutboxStatus.CANCELLED) {
            throw new IllegalStateException("A cancelled provider command cannot be reconciled as processed");
        }
        status = BillingOutboxStatus.PROCESSED;
        claimedAt = null;
        processedAt = Objects.requireNonNull(now);
        lastError = null;
    }

    @PrePersist
    @PreUpdate
    void validateCommand() {
        if ((status == BillingOutboxStatus.PROCESSING) != (claimedAt != null)) {
            throw new IllegalStateException("Outbox processing state and claim evidence must agree");
        }
        if ((status == BillingOutboxStatus.PROCESSED
                || status == BillingOutboxStatus.FAILED
                || status == BillingOutboxStatus.CANCELLED)
                != (processedAt != null)) {
            throw new IllegalStateException("Outbox terminal state and completion evidence must agree");
        }
    }

    private void requireProcessing() {
        if (status != BillingOutboxStatus.PROCESSING) {
            throw new IllegalStateException("Outbox command is not processing");
        }
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Value is required");
        return value.trim();
    }

    private static String safeError(String value) {
        String normalized = value == null || value.isBlank() ? "Provider command failed." : value.trim();
        return normalized.length() <= 2000 ? normalized : normalized.substring(0, 2000);
    }
}
