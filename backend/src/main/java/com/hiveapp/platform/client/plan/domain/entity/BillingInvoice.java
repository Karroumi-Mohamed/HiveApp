package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.BillingInvoiceStatus;
import com.hiveapp.shared.domain.BaseEntity;
import com.hiveapp.shared.money.Money;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "billing_invoices", uniqueConstraints = {
        @UniqueConstraint(name = "uk_billing_invoice_number", columnNames = "invoice_number"),
        @UniqueConstraint(name = "uk_billing_invoice_checkout", columnNames = "checkout_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingInvoice extends BaseEntity {

    @Column(name = "invoice_number", nullable = false, updatable = false, length = 48)
    private String invoiceNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "checkout_id", nullable = false, updatable = false)
    private SubscriptionCheckout checkout;

    @Column(name = "change_operation_id", nullable = false, updatable = false)
    private UUID changeOperationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private BillingInvoiceStatus status;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, updatable = false, length = 20)
    private BillingCycle billingCycle;

    @Column(name = "period_start", updatable = false)
    private Instant periodStart;

    @Column(name = "period_end", updatable = false)
    private Instant periodEnd;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "requested_by_user_id", nullable = false, updatable = false)
    private UUID requestedByUserId;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<BillingInvoiceLine> lines = new ArrayList<>();

    @Version
    @Column(name = "row_version", nullable = false)
    private long version;

    public static BillingInvoice open(
            SubscriptionCheckout checkout,
            Money total,
            BillingCycle billingCycle,
            Instant periodStart,
            Instant periodEnd,
            UUID requestedByUserId,
            Instant issuedAt
    ) {
        Objects.requireNonNull(checkout, "Checkout is required");
        if (checkout.getChangeOperation() == null || checkout.getChangeOperation().getId() == null) {
            throw new IllegalArgumentException("A persisted checkout operation is required");
        }
        BillingInvoice invoice = new BillingInvoice();
        invoice.invoiceNumber = generateNumber(issuedAt);
        invoice.account = checkout.getAccount();
        invoice.checkout = checkout;
        invoice.changeOperationId = checkout.getChangeOperation().getId();
        invoice.status = total.amount().signum() == 0
                ? BillingInvoiceStatus.SETTLED_ZERO : BillingInvoiceStatus.OPEN;
        invoice.setTotal(total);
        invoice.billingCycle = Objects.requireNonNull(billingCycle, "Billing cycle is required");
        invoice.periodStart = periodStart;
        invoice.periodEnd = periodEnd;
        invoice.issuedAt = Objects.requireNonNull(issuedAt, "Invoice issue time is required");
        invoice.requestedByUserId = Objects.requireNonNull(requestedByUserId, "Invoice requester is required");
        if (invoice.status == BillingInvoiceStatus.SETTLED_ZERO) {
            invoice.settledAt = issuedAt;
        }
        return invoice;
    }

    public void addLine(BillingInvoiceLine line) {
        Objects.requireNonNull(line, "Invoice line is required");
        if (status != BillingInvoiceStatus.OPEN && status != BillingInvoiceStatus.SETTLED_ZERO) {
            throw new IllegalStateException("Invoice lines cannot be changed after invoice finalization");
        }
        line.attachTo(this, lines.size());
        lines.add(line);
    }

    public List<BillingInvoiceLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public Money money() {
        return Money.of(totalAmount, currencyCode);
    }

    public void settle(Instant at) {
        if (status == BillingInvoiceStatus.SETTLED) return;
        if (status != BillingInvoiceStatus.OPEN) {
            throw new IllegalStateException("Only an open Invoice can be settled");
        }
        status = BillingInvoiceStatus.SETTLED;
        settledAt = Objects.requireNonNull(at, "Settlement time is required");
    }

    public void cancel() {
        if (status != BillingInvoiceStatus.OPEN) {
            throw new IllegalStateException("Only an open Invoice can be cancelled");
        }
        status = BillingInvoiceStatus.CANCELLED;
    }

    private void setTotal(Money money) {
        if (money.isNegative()) throw new IllegalArgumentException("Invoice total cannot be negative");
        totalAmount = money.amount();
        currencyCode = money.currencyCode();
    }

    @PrePersist
    @PreUpdate
    void validateInvoice() {
        setTotal(Money.of(totalAmount, currencyCode));
        if (periodStart != null && periodEnd != null && !periodEnd.isAfter(periodStart)) {
            throw new IllegalStateException("Invoice period end must be after its start");
        }
        if ((status == BillingInvoiceStatus.SETTLED || status == BillingInvoiceStatus.SETTLED_ZERO)
                != (settledAt != null)) {
            throw new IllegalStateException("Invoice settlement status and evidence must agree");
        }
        Money lineTotal = Money.zero(currencyCode);
        for (BillingInvoiceLine line : lines) lineTotal = lineTotal.add(line.money());
        if (!lineTotal.equals(money())) {
            throw new IllegalStateException("Invoice lines must equal the Invoice total");
        }
    }

    private static String generateNumber(Instant issuedAt) {
        LocalDate date = Objects.requireNonNull(issuedAt).atZone(ZoneOffset.UTC).toLocalDate();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return "INV-" + date.toString().replace("-", "") + "-" + suffix;
    }
}
