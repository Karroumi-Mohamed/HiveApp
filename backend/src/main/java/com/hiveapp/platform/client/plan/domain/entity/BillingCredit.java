package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.shared.domain.BaseEntity;
import com.hiveapp.shared.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "billing_credits", uniqueConstraints =
        @UniqueConstraint(name = "uk_billing_credit_external_reference", columnNames = "external_reference"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingCredit extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private BillingInvoice invoice;

    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, updatable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false, updatable = false, length = 2000)
    private String reason;

    @Column(nullable = false, updatable = false, length = 32)
    private String source;

    @Column(name = "operator_user_id", nullable = false, updatable = false)
    private UUID operatorUserId;

    @Column(name = "external_reference", updatable = false, length = 255)
    private String externalReference;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    public static BillingCredit issue(
            BillingInvoice invoice,
            Money money,
            String reason,
            String source,
            UUID operatorUserId,
            String externalReference,
            Instant issuedAt
    ) {
        if (money.amount().signum() <= 0) throw new IllegalArgumentException("Credit must be positive");
        money.requireSameCurrency(invoice.money());
        BillingCredit credit = new BillingCredit();
        credit.invoice = Objects.requireNonNull(invoice);
        credit.amount = money.amount();
        credit.currencyCode = money.currencyCode();
        credit.reason = requireText(reason);
        credit.source = requireText(source);
        credit.operatorUserId = Objects.requireNonNull(operatorUserId);
        credit.externalReference = externalReference == null || externalReference.isBlank()
                ? null : externalReference.trim();
        credit.issuedAt = Objects.requireNonNull(issuedAt);
        return credit;
    }

    public Money money() { return Money.of(amount, currencyCode); }

    @PrePersist
    void validateCredit() {
        if (Money.of(amount, currencyCode).amount().signum() <= 0) {
            throw new IllegalStateException("Credit must be positive");
        }
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Value is required");
        return value.trim();
    }
}
