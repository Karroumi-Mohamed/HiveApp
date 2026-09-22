package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrides;
import com.hiveapp.shared.domain.BaseEntity;
import com.hiveapp.shared.money.Money;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.AccessLevel;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "subscriptions", indexes = {
        @Index(
                name = "idx_subscriptions_account_created_id",
                columnList = "account_id,created_at,id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_subscriptions_usable_account", columnNames = "usable_account_id")
})
@Getter @Setter
public class Subscription extends BaseEntity {

    @Version
    @Column(nullable = false)
    private long version;

    /** Stable across automatic same-terms renewals, new for any reviewed commercial replacement. */
    @Column(name = "commercial_terms_id")
    private UUID commercialTermsId = UUID.randomUUID();

    @Column(name = "content_evidence_id")
    private UUID contentEvidenceId;

    public UUID termsIdentity() { return commercialTermsId == null ? getId() : commercialTermsId; }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_overrides", nullable = false)
    private SubscriptionOverrides customOverrides = SubscriptionOverrides.empty();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "entitlement_snapshot", nullable = false)
    private SubscriptionEntitlementSnapshot entitlementSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    /**
     * Non-null only while this subscription can provide entitlement. Its uniqueness enforces
     * one entitled-or-grace subscription per account while allowing historical subscriptions.
     */
    @Column(name = "usable_account_id", columnDefinition = """
            uuid check ((status in ('ACTIVE', 'TRIALING', 'PAST_DUE') and usable_account_id is not null and usable_account_id = account_id)
            or (status not in ('ACTIVE', 'TRIALING', 'PAST_DUE') and usable_account_id is null))
            """)
    private UUID usableAccountId;

    /** One non-terminal commercial subscription per Account, including restricted lifecycle states. */
    @Column(name = "current_account_id", unique = true, columnDefinition = """
            uuid check ((status in ('ACTIVE', 'TRIALING', 'PAST_DUE', 'SUSPENDED')
            and current_account_id is not null and current_account_id = account_id)
            or (status not in ('ACTIVE', 'TRIALING', 'PAST_DUE', 'SUSPENDED')
            and current_account_id is null))
            """)
    private UUID currentAccountId;

    /** Queryable terms copied transactionally from the authoritative entitlement snapshot. */
    @Enumerated(EnumType.STRING)
    @Column(name = "snapshot_billing_cycle", length = 20)
    private com.hiveapp.platform.client.plan.domain.constant.BillingCycle snapshotBillingCycle;

    @Column(name = "snapshot_currency_code", length = 3)
    private String snapshotCurrencyCode;

    @OneToMany(mappedBy = "subscription", cascade = CascadeType.ALL, orphanRemoval = true)
    @Setter(AccessLevel.NONE)
    private List<SubscriptionCurrentHolding> currentHoldings = new ArrayList<>();

    @Column(name = "current_period_start", nullable = false)
    private Instant currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd;

    @Column(name = "past_due_at")
    private Instant pastDueAt;

    @Column(name = "grace_ends_at")
    private Instant graceEndsAt;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "suspension_cause", length = 24)
    private com.hiveapp.platform.client.plan.domain.constant.SubscriptionSuspensionCause suspensionCause;

    @Enumerated(EnumType.STRING)
    @Column(name = "suspended_from_status", length = 24)
    private SubscriptionStatus suspendedFromStatus;

    @Column(name = "suspension_reason", length = 2000)
    private String suspensionReason;

    /**
     * Snapshot of the calculated monthly price at the time overrides were last saved.
     * = plan.basePrice + sum(addOnPrices) + sum(quotaBumpCosts).
     * Recalculated by BillingCalculator every time overrides change.
     */
    @Column(name = "current_price", precision = 19, scale = 4)
    private BigDecimal currentPrice;

    @Column(name = "current_price_currency_code", length = 3)
    private String currentPriceCurrencyCode;

    public Money currentMoney() {
        return currentPrice == null ? null : Money.of(currentPrice, currentPriceCurrencyCode);
    }

    public void setCurrentMoney(Money money) {
        if (currentPrice != null && !java.util.Objects.equals(currentMoney(), money)) commercialTermsId = UUID.randomUUID();
        currentPrice = money != null ? money.amount() : null;
        currentPriceCurrencyCode = money != null ? money.currencyCode() : null;
    }

    /** Keeps the portable criteria projection synchronized with every accepted snapshot write. */
    public void setEntitlementSnapshot(SubscriptionEntitlementSnapshot snapshot) {
        if (entitlementSnapshot != null && (snapshot == null || !entitlementSnapshot.withEffectivePeriod(null, null).equals(snapshot.withEffectivePeriod(null, null)))) commercialTermsId = UUID.randomUUID();
        this.entitlementSnapshot = snapshot;
        synchronizeCommercialProjection();
    }

    /** Only the dedicated reviewed content executor may preserve the financial identity here. */
    public void applyContentVersion(Plan target, SubscriptionEntitlementSnapshot snapshot, UUID evidenceId) {
        if (target == null || evidenceId == null || entitlementSnapshot == null
                || !plan.getLineageId().equals(target.getLineageId())
                || !target.getCode().equals(snapshot.planCode())
                || snapshot.financialPlanSource() == null
                || !java.util.Objects.equals(currentPeriodStart, snapshot.effectiveFrom())
                || !java.util.Objects.equals(currentPeriodEnd, snapshot.effectiveUntil())) {
            throw new IllegalStateException("Content application requires reviewed same-family evidence and unchanged period.");
        }
        UUID retainedTerms = termsIdentity();
        setEntitlementSnapshot(snapshot);
        plan = target;
        contentEvidenceId = evidenceId;
        commercialTermsId = retainedTerms;
    }

    public void setCustomOverrides(SubscriptionOverrides overrides) {
        if (getId() != null && !java.util.Objects.equals(customOverrides, overrides)) commercialTermsId = UUID.randomUUID();
        customOverrides = overrides;
    }

    public List<SubscriptionCurrentHolding> getCurrentHoldings() {
        return java.util.Collections.unmodifiableList(currentHoldings);
    }

    @PrePersist
    @PreUpdate
    void synchronizeUsableAccountSlot() {
        boolean usable = status == SubscriptionStatus.ACTIVE
                || status == SubscriptionStatus.TRIALING
                || status == SubscriptionStatus.PAST_DUE;
        usableAccountId = usable && account != null ? account.getId() : null;
        boolean currentLifecycle = status == SubscriptionStatus.ACTIVE
                || status == SubscriptionStatus.TRIALING
                || status == SubscriptionStatus.PAST_DUE
                || status == SubscriptionStatus.SUSPENDED;
        currentAccountId = currentLifecycle && account != null ? account.getId() : null;
        if (entitlementSnapshot == null) {
            throw new IllegalStateException("Subscription entitlement snapshot is required");
        }
        if (currentPrice != null) {
            Money current = Money.of(currentPrice, currentPriceCurrencyCode);
            Money.zero(entitlementSnapshot.currencyCode()).requireSameCurrency(current);
            setCurrentMoney(current);
        } else {
            currentPriceCurrencyCode = null;
        }
        if (customOverrides == null) {
            customOverrides = SubscriptionOverrides.empty();
        }
        if (currentPeriodStart == null || currentPeriodEnd == null || !currentPeriodEnd.isAfter(currentPeriodStart)) {
            throw new IllegalStateException("Subscription requires a valid current period");
        }
        if (status == SubscriptionStatus.PAST_DUE
                && (pastDueAt == null || graceEndsAt == null || !graceEndsAt.isAfter(pastDueAt))) {
            throw new IllegalStateException("A past-due subscription requires a future grace deadline");
        }
        if (status == SubscriptionStatus.SUSPENDED && suspensionCause == null) {
            throw new IllegalStateException("A suspended subscription requires a suspension cause");
        }
        if (suspensionCause == com.hiveapp.platform.client.plan.domain.constant.SubscriptionSuspensionCause.OPERATOR
                && suspendedFromStatus != SubscriptionStatus.ACTIVE
                && suspendedFromStatus != SubscriptionStatus.TRIALING) {
            throw new IllegalStateException("Operator suspension requires its prior entitled status");
        }
    }

    private void synchronizeCommercialProjection() {
        if (entitlementSnapshot == null) {
            snapshotBillingCycle = null;
            snapshotCurrencyCode = null;
            currentHoldings.clear();
            return;
        }
        snapshotBillingCycle = entitlementSnapshot.billingCycle();
        snapshotCurrencyCode = entitlementSnapshot.currencyCode() == null
                ? null : entitlementSnapshot.currencyCode().trim().toUpperCase(java.util.Locale.ROOT);
        LinkedHashSet<String> desired = new LinkedHashSet<>();
        collectHolding(desired, com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentProductType.PLAN,
                entitlementSnapshot.planCode());
        entitlementSnapshot.addOns().forEach(item -> collectHolding(desired,
                com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentProductType.ADD_ON,
                item.code()));
        entitlementSnapshot.quotaPackages().forEach(item -> collectHolding(desired,
                com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentProductType.QUOTA_PACKAGE,
                item.code()));
        currentHoldings.removeIf(holding -> !desired.contains(holdingKey(
                holding.getProductType(), holding.getProductCode())));
        LinkedHashSet<String> existing = currentHoldings.stream()
                .map(holding -> holdingKey(holding.getProductType(), holding.getProductCode()))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (String key : desired) {
            if (existing.add(key)) {
                int separator = key.indexOf(':');
                var type = com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentProductType
                        .valueOf(key.substring(0, separator));
                currentHoldings.add(SubscriptionCurrentHolding.of(
                        this, type, key.substring(separator + 1)));
            }
        }
        synchronizeHoldingPrices();
    }

    public void synchronizeHoldingPrices() {
        if (entitlementSnapshot == null) return;
        for (var holding : currentHoldings) {
            holding.setPriceEntryId(switch (holding.getProductType()) {
                case PLAN -> entitlementSnapshot.planPriceEntryId();
                case ADD_ON -> entitlementSnapshot.addOns().stream()
                        .filter(a -> a.code().equalsIgnoreCase(holding.getProductCode()))
                        .map(a -> java.util.Optional.ofNullable(a.priceEntryId())).findFirst()
                        .flatMap(java.util.function.Function.identity()).orElse(null);
                case QUOTA_PACKAGE -> entitlementSnapshot.quotaPackages().stream()
                        .filter(p -> p.code().equalsIgnoreCase(holding.getProductCode()))
                        .map(p -> java.util.Optional.ofNullable(p.priceEntryId())).findFirst()
                        .flatMap(java.util.function.Function.identity()).orElse(null);
            });
        }
    }

    private void collectHolding(
            LinkedHashSet<String> keys,
            com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentProductType type,
            String code
    ) {
        if (code == null || code.isBlank()) return;
        String normalized = code.trim().toUpperCase(java.util.Locale.ROOT);
        keys.add(holdingKey(type, normalized));
    }

    private String holdingKey(
            com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentProductType type,
            String code
    ) {
        return type.name() + ":" + code.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
