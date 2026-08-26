package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus;
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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An authoritative recurring price for one exact commercial-product database revision.
 * Published terms are intentionally mutated only through lifecycle methods; changing terms creates
 * a successor draft instead.
 */
@Entity
@Table(name = "product_prices", uniqueConstraints = {
        @UniqueConstraint(name = "uk_product_price_lineage_revision",
                columnNames = {"lineage_id", "revision_number"})
})
@Check(constraints = "amount >= 0 and (effective_until is null or effective_until > effective_from)"
        + " and ((owner_type = 'PLAN' and plan_id is not null and add_on_id is null and quota_package_id is null)"
        + " or (owner_type = 'ADD_ON' and plan_id is null and add_on_id is not null and quota_package_id is null)"
        + " or (owner_type = 'QUOTA_PACKAGE' and plan_id is null and add_on_id is null and quota_package_id is not null))")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductPrice extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, updatable = false, length = 20)
    private ProductPriceOwnerType ownerType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", updatable = false)
    private Plan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "add_on_id", updatable = false)
    private AddOn addOn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quota_package_id", updatable = false)
    private QuotaPackage quotaPackage;

    @Column(nullable = false, precision = 19, scale = 4)
    private java.math.BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 20)
    private BillingCycle billingCycle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductPriceStatus status = ProductPriceStatus.DRAFT;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_until")
    private Instant effectiveUntil;

    @Column(name = "lineage_id", nullable = false, updatable = false)
    private UUID lineageId;

    @Column(name = "revision_number", nullable = false, updatable = false)
    private int revisionNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_price_id", updatable = false)
    private ProductPrice sourcePrice;

    @Column(name = "compatibility_default", nullable = false)
    private boolean compatibilityDefault;

    @Version
    @Column(name = "row_version", nullable = false)
    private long version;

    public static ProductPrice draft(Plan owner, Money money, BillingCycle cycle,
                                     Instant effectiveFrom, Instant effectiveUntil) {
        ProductPrice price = owner(ProductPriceOwnerType.PLAN, owner, null, null);
        price.initializeTerms(money, cycle, effectiveFrom, effectiveUntil);
        return price;
    }

    public static ProductPrice draft(AddOn owner, Money money, BillingCycle cycle,
                                     Instant effectiveFrom, Instant effectiveUntil) {
        ProductPrice price = owner(ProductPriceOwnerType.ADD_ON, null, owner, null);
        price.initializeTerms(money, cycle, effectiveFrom, effectiveUntil);
        return price;
    }

    public static ProductPrice draft(QuotaPackage owner, Money money, BillingCycle cycle,
                                     Instant effectiveFrom, Instant effectiveUntil) {
        ProductPrice price = owner(ProductPriceOwnerType.QUOTA_PACKAGE, null, null, owner);
        price.initializeTerms(money, cycle, effectiveFrom, effectiveUntil);
        return price;
    }

    private static ProductPrice owner(ProductPriceOwnerType ownerType, Plan plan, AddOn addOn,
                                      QuotaPackage quotaPackage) {
        ProductPrice price = new ProductPrice();
        price.ownerType = ownerType;
        price.plan = plan;
        price.addOn = addOn;
        price.quotaPackage = quotaPackage;
        price.lineageId = UUID.randomUUID();
        price.revisionNumber = 1;
        return price;
    }

    public ProductPrice revise() {
        requirePublished("revise");
        ProductPrice successor = owner(ownerType, plan, addOn, quotaPackage);
        successor.initializeTerms(money(), billingCycle, effectiveFrom, effectiveUntil);
        successor.lineageId = lineageId;
        successor.revisionNumber = revisionNumber + 1;
        successor.sourcePrice = this;
        return successor;
    }

    public void editDraft(Money money, BillingCycle cycle, Instant from, Instant until) {
        requireStatus(ProductPriceStatus.DRAFT, "Only draft price entries can be edited.");
        initializeTerms(money, cycle, from, until);
    }

    public void activate() {
        if (status != ProductPriceStatus.DRAFT && status != ProductPriceStatus.INACTIVE) {
            throw new IllegalStateException("Only draft or inactive price entries can be activated.");
        }
        status = ProductPriceStatus.ACTIVE;
    }

    public void pause() {
        requireStatus(ProductPriceStatus.ACTIVE, "Only active price entries can be paused.");
        status = ProductPriceStatus.INACTIVE;
    }

    public void archive() {
        if (status == ProductPriceStatus.ACTIVE) {
            throw new IllegalStateException("An active price entry must be paused before it is archived.");
        }
        if (status == ProductPriceStatus.ARCHIVED) {
            throw new IllegalStateException("An archived price entry is terminal.");
        }
        status = ProductPriceStatus.ARCHIVED;
    }

    /**
     * Atomically closes new-sale applicability for a published price at the exact start of its
     * successor. This is the only supported mutation of a published effective window; it may
     * shorten, but never extend, the historical window.
     */
    public void endForReplacementAt(Instant cutoff) {
        requireStatus(ProductPriceStatus.ACTIVE,
                "Only an active price entry can be bounded by a scheduled replacement.");
        Objects.requireNonNull(cutoff, "Replacement cutoff is required");
        if (!cutoff.isAfter(effectiveFrom)) {
            throw new IllegalStateException("Replacement cutoff must be after the current price starts.");
        }
        if (effectiveUntil != null && cutoff.isAfter(effectiveUntil)) {
            throw new IllegalStateException("Replacement cutoff cannot extend the current price window.");
        }
        effectiveUntil = cutoff;
    }

    public void markCompatibilityDefault() {
        requireStatus(ProductPriceStatus.DRAFT, "Compatibility metadata can only be set on a draft.");
        compatibilityDefault = true;
    }

    public UUID ownerId() {
        return switch (ownerType) {
            case PLAN -> plan.getId();
            case ADD_ON -> addOn.getId();
            case QUOTA_PACKAGE -> quotaPackage.getId();
        };
    }

    public String ownerCode() {
        return switch (ownerType) {
            case PLAN -> plan.getCode();
            case ADD_ON -> addOn.getCode();
            case QUOTA_PACKAGE -> quotaPackage.getCode();
        };
    }

    public String ownerName() {
        return switch (ownerType) {
            case PLAN -> plan.getName();
            case ADD_ON -> addOn.getName();
            case QUOTA_PACKAGE -> quotaPackage.getName();
        };
    }

    public Money money() {
        return Money.of(amount, currencyCode);
    }

    public boolean isApplicableAt(Instant instant) {
        return status == ProductPriceStatus.ACTIVE
                && !effectiveFrom.isAfter(instant)
                && (effectiveUntil == null || effectiveUntil.isAfter(instant));
    }

    private void initializeTerms(Money money, BillingCycle cycle, Instant from, Instant until) {
        Objects.requireNonNull(money, "Price money is required");
        if (money.isNegative()) {
            throw new IllegalArgumentException("Price amount cannot be negative.");
        }
        Objects.requireNonNull(cycle, "Billing cycle is required");
        Objects.requireNonNull(from, "Price effectiveFrom is required");
        if (until != null && !until.isAfter(from)) {
            throw new IllegalArgumentException("Price effectiveUntil must be after effectiveFrom.");
        }
        amount = money.amount();
        currencyCode = money.currencyCode();
        billingCycle = cycle;
        effectiveFrom = from;
        effectiveUntil = until;
    }

    private void requirePublished(String action) {
        if (status != ProductPriceStatus.ACTIVE && status != ProductPriceStatus.INACTIVE) {
            throw new IllegalStateException("Only a published price entry can be " + action + "d.");
        }
    }

    private void requireStatus(ProductPriceStatus required, String message) {
        if (status != required) {
            throw new IllegalStateException(message);
        }
    }

    @PrePersist
    @PreUpdate
    void validateInvariant() {
        int owners = (plan == null ? 0 : 1) + (addOn == null ? 0 : 1) + (quotaPackage == null ? 0 : 1);
        if (owners != 1 || ownerType == null
                || (ownerType == ProductPriceOwnerType.PLAN) != (plan != null)
                || (ownerType == ProductPriceOwnerType.ADD_ON) != (addOn != null)
                || (ownerType == ProductPriceOwnerType.QUOTA_PACKAGE) != (quotaPackage != null)) {
            throw new IllegalStateException("A price entry must own exactly one matching product revision.");
        }
        initializeTerms(Money.of(amount, currencyCode), billingCycle, effectiveFrom, effectiveUntil);
        if (lineageId == null || revisionNumber < 1) {
            throw new IllegalStateException("Price lineage identity is required.");
        }
    }
}
