package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrides;
import com.hiveapp.shared.domain.BaseEntity;
import com.hiveapp.shared.money.Money;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscriptions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_subscriptions_usable_account", columnNames = "usable_account_id")
})
@Getter @Setter
public class Subscription extends BaseEntity {

    @Version
    @Column(nullable = false)
    private long version;

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
     * one ACTIVE or TRIALING subscription per account while allowing historical subscriptions.
     */
    @Column(name = "usable_account_id", columnDefinition = """
            uuid check ((status in ('ACTIVE', 'TRIALING') and usable_account_id is not null and usable_account_id = account_id)
            or (status not in ('ACTIVE', 'TRIALING') and usable_account_id is null))
            """)
    private UUID usableAccountId;

    @Column(name = "current_period_start", nullable = false)
    private Instant currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd;

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
        currentPrice = money != null ? money.amount() : null;
        currentPriceCurrencyCode = money != null ? money.currencyCode() : null;
    }

    @PrePersist
    @PreUpdate
    void synchronizeUsableAccountSlot() {
        boolean usable = status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.TRIALING;
        usableAccountId = usable && account != null ? account.getId() : null;
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
    }
}
