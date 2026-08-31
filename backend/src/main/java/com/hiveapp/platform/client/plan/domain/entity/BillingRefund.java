package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.client.plan.domain.constant.BillingPaymentStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingRefundStatus;
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
@Table(name = "billing_refunds", uniqueConstraints = {
        @UniqueConstraint(name = "uk_billing_refund_idempotency", columnNames = "idempotency_key"),
        @UniqueConstraint(name = "uk_billing_refund_provider_reference", columnNames = "provider_reference")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingRefund extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    private BillingPaymentAttempt payment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BillingRefundStatus status;

    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, updatable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false, updatable = false, length = 2000)
    private String reason;

    @Column(name = "operator_user_id", nullable = false, updatable = false)
    private UUID operatorUserId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 160)
    private String idempotencyKey;

    @Column(name = "provider_reference", length = 255)
    private String providerReference;

    @Column(name = "failure_reason", length = 2000)
    private String failureReason;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long version;

    public static BillingRefund pending(
            BillingPaymentAttempt payment,
            Money money,
            String reason,
            UUID operatorUserId,
            String idempotencyKey
    ) {
        if (payment.getStatus() != BillingPaymentStatus.SUCCEEDED
                || !payment.isTrustedForSettlement()) {
            throw new IllegalArgumentException("Only a trusted succeeded Payment can be refunded");
        }
        if (money.amount().signum() <= 0) throw new IllegalArgumentException("Refund must be positive");
        money.requireSameCurrency(payment.money());
        BillingRefund refund = new BillingRefund();
        refund.payment = payment;
        refund.status = BillingRefundStatus.PENDING;
        refund.amount = money.amount();
        refund.currencyCode = money.currencyCode();
        refund.reason = requireText(reason);
        refund.operatorUserId = Objects.requireNonNull(operatorUserId);
        refund.idempotencyKey = requireText(idempotencyKey);
        return refund;
    }

    public void recordProviderResult(PaymentResult result, Instant at) {
        if (status != BillingRefundStatus.PENDING) {
            throw new IllegalStateException("Only a pending Refund may record a provider result");
        }
        if (result == null || result.status() == null) {
            throw new IllegalArgumentException("Provider refund result status is required");
        }
        providerReference = result.transactionId() == null || result.transactionId().isBlank()
                ? null : result.transactionId().trim();
        if (result.status() == PaymentStatus.PENDING) return;
        status = result.status() == PaymentStatus.SUCCESS
                ? BillingRefundStatus.SUCCEEDED : BillingRefundStatus.FAILED;
        failureReason = status == BillingRefundStatus.FAILED
                ? (result.failureReason() == null || result.failureReason().isBlank()
                ? "Payment provider declined the Refund." : result.failureReason().trim()) : null;
        completedAt = Objects.requireNonNull(at);
    }

    public void recordTransportFailure(String reason, Instant at) {
        if (status != BillingRefundStatus.PENDING) {
            throw new IllegalStateException("Only a pending Refund may fail transport");
        }
        status = BillingRefundStatus.FAILED;
        failureReason = reason == null || reason.isBlank()
                ? "Refund provider command failed." : reason.trim();
        completedAt = Objects.requireNonNull(at);
    }

    public Money money() { return Money.of(amount, currencyCode); }

    @PrePersist
    @PreUpdate
    void validateRefund() {
        if (Money.of(amount, currencyCode).amount().signum() <= 0) {
            throw new IllegalStateException("Refund must be positive");
        }
        boolean terminal = status != BillingRefundStatus.PENDING;
        if (terminal != (completedAt != null)) {
            throw new IllegalStateException("Refund terminal state and completion evidence must agree");
        }
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Value is required");
        return value.trim();
    }
}
