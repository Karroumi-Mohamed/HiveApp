package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxOperation;
import com.hiveapp.platform.client.plan.domain.constant.BillingProviderEventStatus;
import com.hiveapp.platform.client.plan.domain.model.VerifiedBillingProviderEvent;
import com.hiveapp.shared.domain.BaseEntity;
import com.hiveapp.shared.payment.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "billing_provider_events", uniqueConstraints =
        @UniqueConstraint(name = "uk_billing_provider_event", columnNames = {"provider", "event_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingProviderEvent extends BaseEntity {
    @Column(nullable = false, updatable = false, length = 64)
    private String provider;

    @Column(name = "event_id", nullable = false, updatable = false, length = 255)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private BillingOutboxOperation operation;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_status", nullable = false, updatable = false, length = 16)
    private PaymentStatus providerStatus;

    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, updatable = false, length = 3)
    private String currencyCode;

    @Column(name = "idempotency_key", updatable = false, length = 160)
    private String idempotencyKey;

    @Column(name = "provider_reference", updatable = false, length = 255)
    private String providerReference;

    @Column(name = "failure_reason", updatable = false, length = 2000)
    private String failureReason;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "payload_digest", nullable = false, updatable = false, length = 64)
    private String payloadDigest;

    @Column(name = "trusted_for_settlement", nullable = false, updatable = false)
    private boolean trustedForSettlement;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 16)
    private BillingProviderEventStatus processingStatus;

    @Column(name = "aggregate_id")
    private UUID aggregateId;

    @Column(name = "outbox_command_id")
    private UUID outboxCommandId;

    @Column(name = "attention_reason", length = 2000)
    private String attentionReason;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long version;

    public static BillingProviderEvent received(VerifiedBillingProviderEvent evidence) {
        Objects.requireNonNull(evidence, "Provider evidence is required");
        BillingProviderEvent event = new BillingProviderEvent();
        event.provider = evidence.provider();
        event.eventId = evidence.eventId();
        event.operation = evidence.operation();
        event.providerStatus = evidence.status();
        event.amount = evidence.amount();
        event.currencyCode = evidence.currencyCode();
        event.idempotencyKey = evidence.idempotencyKey();
        event.providerReference = evidence.providerReference();
        event.failureReason = evidence.failureReason();
        event.occurredAt = evidence.occurredAt();
        event.payloadDigest = evidence.payloadDigest();
        event.trustedForSettlement = evidence.trustedForSettlement();
        event.processingStatus = BillingProviderEventStatus.RECEIVED;
        return event;
    }

    public void applied(UUID aggregateId, UUID commandId, Instant at) {
        this.aggregateId = Objects.requireNonNull(aggregateId);
        this.outboxCommandId = Objects.requireNonNull(commandId);
        processingStatus = BillingProviderEventStatus.APPLIED;
        attentionReason = null;
        processedAt = Objects.requireNonNull(at);
    }

    public void unmatched(String reason, Instant at) {
        markAttention(BillingProviderEventStatus.UNMATCHED, null, null, reason, at);
    }

    public void mismatched(UUID aggregateId, UUID commandId, String reason, Instant at) {
        markAttention(BillingProviderEventStatus.MISMATCHED, aggregateId, commandId, reason, at);
    }

    private void markAttention(
            BillingProviderEventStatus status,
            UUID aggregateId,
            UUID commandId,
            String reason,
            Instant at
    ) {
        this.aggregateId = aggregateId;
        this.outboxCommandId = commandId;
        processingStatus = status;
        attentionReason = safeReason(reason);
        processedAt = Objects.requireNonNull(at);
    }

    @PrePersist
    @PreUpdate
    void validateEvent() {
        Objects.requireNonNull(operation, "Provider event operation is required");
        Objects.requireNonNull(providerStatus, "Provider event status is required");
        Objects.requireNonNull(processingStatus, "Provider event processing status is required");
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalStateException("Provider event amount must be positive");
        }
        if (processingStatus == BillingProviderEventStatus.RECEIVED && processedAt != null) {
            throw new IllegalStateException("Unprocessed provider evidence cannot have a processed time");
        }
        if (processingStatus != BillingProviderEventStatus.RECEIVED && processedAt == null) {
            throw new IllegalStateException("Processed provider evidence requires a processed time");
        }
    }

    private static String safeReason(String value) {
        String normalized = value == null || value.isBlank()
                ? "Provider evidence requires review." : value.trim();
        return normalized.length() <= 2000 ? normalized : normalized.substring(0, 2000);
    }
}
