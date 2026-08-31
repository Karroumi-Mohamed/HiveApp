package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.client.plan.domain.constant.BillingLineType;
import com.hiveapp.shared.domain.BaseEntity;
import com.hiveapp.shared.money.Money;
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
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "billing_invoice_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingInvoiceLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private BillingInvoice invoice;

    @Column(nullable = false, updatable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", nullable = false, updatable = false, length = 32)
    private BillingLineType type;

    @Column(name = "source_code", nullable = false, updatable = false, length = 160)
    private String sourceCode;

    @Column(name = "source_name", nullable = false, updatable = false, length = 255)
    private String sourceName;

    @Column(name = "source_version", nullable = false, updatable = false)
    private long sourceVersion;

    @Column(name = "price_entry_id", updatable = false)
    private UUID priceEntryId;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "unit_amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal unitAmount;

    @Column(name = "line_amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal lineAmount;

    @Column(name = "currency_code", nullable = false, updatable = false, length = 3)
    private String currencyCode;

    public static BillingInvoiceLine component(
            BillingLineType type,
            String code,
            String name,
            long version,
            UUID priceEntryId,
            int quantity,
            Money unitPrice
    ) {
        if (type == BillingLineType.COMMERCIAL_ADJUSTMENT) {
            throw new IllegalArgumentException("Use adjustment() for signed commercial adjustments");
        }
        if (quantity < 1) throw new IllegalArgumentException("Invoice line quantity must be positive");
        BillingInvoiceLine line = base(type, code, name, version, priceEntryId, quantity, unitPrice);
        line.lineAmount = unitPrice.multiply(quantity).amount();
        return line;
    }

    public static BillingInvoiceLine adjustment(String name, Money amount) {
        BillingInvoiceLine line = base(
                BillingLineType.COMMERCIAL_ADJUSTMENT,
                "COMMERCIAL_ADJUSTMENT",
                name,
                0,
                null,
                1,
                amount);
        line.lineAmount = amount.amount();
        return line;
    }

    private static BillingInvoiceLine base(
            BillingLineType type,
            String code,
            String name,
            long version,
            UUID priceEntryId,
            int quantity,
            Money unitPrice
    ) {
        BillingInvoiceLine line = new BillingInvoiceLine();
        line.type = Objects.requireNonNull(type);
        line.sourceCode = requireText(code, "Invoice line source code is required");
        line.sourceName = requireText(name, "Invoice line source name is required");
        line.sourceVersion = version;
        line.priceEntryId = priceEntryId;
        line.quantity = quantity;
        line.unitAmount = unitPrice.amount();
        line.currencyCode = unitPrice.currencyCode();
        return line;
    }

    void attachTo(BillingInvoice invoice, int position) {
        if (this.invoice != null && this.invoice != invoice) {
            throw new IllegalStateException("Invoice line is already attached");
        }
        this.invoice = Objects.requireNonNull(invoice);
        this.position = position;
    }

    public Money money() {
        return Money.of(lineAmount, currencyCode);
    }

    @PrePersist
    @PreUpdate
    void validateLine() {
        Money unit = Money.of(unitAmount, currencyCode);
        Money amount = Money.of(lineAmount, currencyCode);
        if (type != BillingLineType.COMMERCIAL_ADJUSTMENT
                && !unit.multiply(quantity).equals(amount)) {
            throw new IllegalStateException("Invoice component line amount must equal unit price times quantity");
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }
}
