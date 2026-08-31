package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.client.plan.domain.constant.BillingPaymentKind;
import com.hiveapp.platform.client.plan.domain.constant.BillingPaymentStatus;
import com.hiveapp.shared.domain.BaseEntity;
import com.hiveapp.shared.money.Money;
import com.hiveapp.shared.payment.PaymentResult;
import com.hiveapp.shared.payment.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "billing_payment_attempts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_billing_payment_idempotency", columnNames = "idempotency_key"),
        @UniqueConstraint(name = "uk_billing_payment_external_reference", columnNames = "external_reference")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingPaymentAttempt extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private BillingInvoice invoice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private BillingPaymentKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BillingPaymentStatus status;

    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, updatable = false, length = 3)
    private String currencyCode;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 160)
    private String idempotencyKey;

    @Column(name = "external_reference", length = 255)
    private String externalReference;

    @Column(name = "failure_reason", length = 2000)
    private String failureReason;

    @Column(name = "trusted_for_settlement", nullable = false)
    private boolean trustedForSettlement;

    @Column(name = "operator_user_id")
    private UUID operatorUserId;

    @Column(name = "operator_reason", length = 2000)
    private String operatorReason;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long version;

    public static BillingPaymentAttempt pendingProvider(
            BillingInvoice invoice,
            String idempotencyKey
    ) {
        BillingPaymentAttempt attempt = base(
                invoice, BillingPaymentKind.PROVIDER, idempotencyKey);
        attempt.status = BillingPaymentStatus.PENDING;
        return attempt;
    }

    public static BillingPaymentAttempt manualSucceeded(
            BillingInvoice invoice,
            UUID operatorUserId,
            String externalReference,
            String reason,
            Instant completedAt
    ) {
        BillingPaymentAttempt attempt = base(
                invoice, BillingPaymentKind.MANUAL, "manual:" + requireText(externalReference));
        attempt.status = BillingPaymentStatus.SUCCEEDED;
        attempt.trustedForSettlement = true;
        attempt.externalReference = requireText(externalReference);
        attempt.operatorUserId = Objects.requireNonNull(operatorUserId, "Manual settlement operator is required");
        attempt.operatorReason = requireText(reason);
        attempt.completedAt = Objects.requireNonNull(completedAt, "Manual settlement time is required");
        return attempt;
    }

    private static BillingPaymentAttempt base(
            BillingInvoice invoice,
            BillingPaymentKind kind,
            String idempotencyKey
    ) {
        BillingPaymentAttempt attempt = new BillingPaymentAttempt();
        attempt.invoice = Objects.requireNonNull(invoice, "Invoice is required");
        attempt.kind = Objects.requireNonNull(kind);
        attempt.setMoney(invoice.money());
        attempt.idempotencyKey = requireText(idempotencyKey);
        return attempt;
    }

    public void recordProviderResult(PaymentResult result, boolean trustedForSettlement, Instant at) {
        if (kind != BillingPaymentKind.PROVIDER || status != BillingPaymentStatus.PENDING) {
            throw new IllegalStateException("Only a pending provider attempt may record a provider result");
        }
        Objects.requireNonNull(result, "Provider result is required");
        if (result.status() == null) {
            throw new IllegalArgumentException("Provider result status is required");
        }
        externalReference = trimToNull(result.transactionId());
        if (result.status() == PaymentStatus.PENDING) {
            trustedForSettlement = false;
            return;
        }
        status = result.status() == PaymentStatus.SUCCESS
                ? BillingPaymentStatus.SUCCEEDED : BillingPaymentStatus.FAILED;
        this.trustedForSettlement = status == BillingPaymentStatus.SUCCEEDED && trustedForSettlement;
        failureReason = status == BillingPaymentStatus.FAILED
                ? defaultFailure(result.failureReason()) : null;
        completedAt = Objects.requireNonNull(at, "Attempt completion time is required");
    }

    public void recordTransportFailure(String reason, Instant at) {
        if (kind != BillingPaymentKind.PROVIDER || status != BillingPaymentStatus.PENDING) {
            throw new IllegalStateException("Only a pending provider attempt may fail transport");
        }
        status = BillingPaymentStatus.FAILED;
        trustedForSettlement = false;
        failureReason = defaultFailure(reason);
        completedAt = Objects.requireNonNull(at);
    }

    public void cancel(String reason, Instant at) {
        if (kind != BillingPaymentKind.PROVIDER || status != BillingPaymentStatus.PENDING) {
            throw new IllegalStateException("Only a pending provider attempt may be cancelled");
        }
        status = BillingPaymentStatus.CANCELLED;
        trustedForSettlement = false;
        failureReason = requireText(reason);
        completedAt = Objects.requireNonNull(at, "Attempt cancellation time is required");
    }

    public Money money() {
        return Money.of(amount, currencyCode);
    }

    @PrePersist
    @PreUpdate
    void validateAttempt() {
        setMoney(Money.of(amount, currencyCode));
        if (amount.signum() <= 0) throw new IllegalStateException("Payment amount must be positive");
        boolean terminal = status == BillingPaymentStatus.SUCCEEDED
                || status == BillingPaymentStatus.FAILED
                || status == BillingPaymentStatus.CANCELLED;
        if (terminal != (completedAt != null)) {
            throw new IllegalStateException("Payment terminal state and completion evidence must agree");
        }
        if (kind == BillingPaymentKind.MANUAL
                && (status != BillingPaymentStatus.SUCCEEDED || operatorUserId == null
                || operatorReason == null || externalReference == null)) {
            throw new IllegalStateException("Manual settlement requires operator evidence");
        }
    }

    private void setMoney(Money money) {
        amount = money.amount();
        currencyCode = money.currencyCode();
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Value is required");
        return value.trim();
    }

    private static String defaultFailure(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? "Payment provider declined the attempt." : normalized;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
