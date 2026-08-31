package com.hiveapp.platform.client.plan.domain.model;

import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxOperation;
import com.hiveapp.shared.money.Money;
import com.hiveapp.shared.payment.PaymentResult;
import com.hiveapp.shared.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/** Evidence produced only after a provider adapter has verified the callback signature. */
public record VerifiedBillingProviderEvent(
        String provider,
        String eventId,
        BillingOutboxOperation operation,
        PaymentStatus status,
        BigDecimal amount,
        String currencyCode,
        String idempotencyKey,
        String providerReference,
        String failureReason,
        Instant occurredAt,
        String payloadDigest,
        boolean trustedForSettlement
) {
    public VerifiedBillingProviderEvent {
        provider = requireText(provider, 64, "Provider is required").toLowerCase(Locale.ROOT);
        eventId = requireText(eventId, 255, "Provider event id is required");
        operation = Objects.requireNonNull(operation, "Provider event operation is required");
        status = Objects.requireNonNull(status, "Provider event status is required");
        Money money = Money.of(amount, currencyCode);
        if (money.amount().signum() <= 0) throw new IllegalArgumentException("Event amount must be positive");
        amount = money.amount();
        currencyCode = money.currencyCode();
        idempotencyKey = trimToNull(idempotencyKey, 160, "Idempotency key is too long");
        providerReference = trimToNull(providerReference, 255, "Provider reference is too long");
        failureReason = trimToNull(failureReason, 2000, "Failure reason is too long");
        occurredAt = Objects.requireNonNull(occurredAt, "Provider event time is required");
        payloadDigest = requireDigest(payloadDigest);
        if (status == PaymentStatus.SUCCESS && providerReference == null) {
            throw new IllegalArgumentException("Successful provider evidence requires a reference");
        }
    }

    public PaymentResult paymentResult() {
        return new PaymentResult(providerReference, status, failureReason);
    }

    private static String requireDigest(String value) {
        String normalized = requireText(value, 64, "Payload digest is required").toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Payload digest must be a SHA-256 hex value");
        }
        return normalized;
    }

    private static String requireText(String value, int maximum, String message) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(message);
        if (normalized.length() > maximum) throw new IllegalArgumentException(message);
        return normalized;
    }

    private static String trimToNull(String value, int maximum, String message) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > maximum) throw new IllegalArgumentException(message);
        return normalized;
    }
}
