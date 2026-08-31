package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyEffectType;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyProductType;
import com.hiveapp.platform.registry.domain.entity.Feature;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Objects;

/** One typed, closed-vocabulary effect owned by an immutable policy revision. */
@Entity
@Table(name = "commercial_policy_effects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommercialPolicyEffect extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false, updatable = false)
    private CommercialPolicy policy;

    @Column(name = "effect_order", nullable = false)
    private int effectOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "effect_type", nullable = false, length = 40)
    private CommercialPolicyEffectType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", length = 24)
    private CommercialPolicyProductType productType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private Plan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "add_on_id")
    private AddOn addOn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quota_package_id")
    private QuotaPackage quotaPackage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feature_id")
    private Feature feature;

    @Column(name = "quota_resource", length = 100)
    private String quotaResource;

    @Column(name = "quantity_delta")
    private Long quantityDelta;

    @Column(name = "money_amount", precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "money_currency_code", length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", length = 20)
    private BillingCycle billingCycle;

    @Column(name = "percentage", precision = 7, scale = 4)
    private BigDecimal percentage;

    @Column(name = "maximum_amount", precision = 19, scale = 4)
    private BigDecimal maximumAmount;

    @Column(name = "maximum_currency_code", length = 3)
    private String maximumCurrencyCode;

    public static CommercialPolicyEffect create(
            CommercialPolicy policy,
            int effectOrder,
            CommercialPolicyEffectType type,
            CommercialPolicyProductType productType,
            Plan plan,
            AddOn addOn,
            QuotaPackage quotaPackage,
            Feature feature,
            String quotaResource,
            Long quantityDelta,
            Money money,
            BillingCycle billingCycle,
            BigDecimal percentage,
            Money maximum
    ) {
        CommercialPolicyEffect effect = new CommercialPolicyEffect();
        effect.policy = Objects.requireNonNull(policy, "Policy is required");
        effect.effectOrder = effectOrder;
        effect.type = Objects.requireNonNull(type, "Effect type is required");
        effect.productType = productType;
        effect.plan = plan;
        effect.addOn = addOn;
        effect.quotaPackage = quotaPackage;
        effect.feature = feature;
        effect.quotaResource = normalize(quotaResource);
        effect.quantityDelta = quantityDelta;
        effect.amount = money == null ? null : money.amount();
        effect.currencyCode = money == null ? null : money.currencyCode();
        effect.billingCycle = billingCycle;
        effect.percentage = percentage;
        effect.maximumAmount = maximum == null ? null : maximum.amount();
        effect.maximumCurrencyCode = maximum == null ? null : maximum.currencyCode();
        effect.validateInvariant();
        return effect;
    }

    public Money money() {
        return amount == null ? null : Money.of(amount, currencyCode);
    }

    public Money maximumMoney() {
        return maximumAmount == null ? null : Money.of(maximumAmount, maximumCurrencyCode);
    }

    public java.util.UUID productId() {
        if (plan != null) return plan.getId();
        if (addOn != null) return addOn.getId();
        return quotaPackage == null ? null : quotaPackage.getId();
    }

    public String productCode() {
        if (plan != null) return plan.getCode();
        if (addOn != null) return addOn.getCode();
        return quotaPackage == null ? null : quotaPackage.getCode();
    }

    @PrePersist
    @PreUpdate
    void validateInvariant() {
        if (effectOrder < 0 || type == null) {
            throw new IllegalStateException("Commercial policy effect identity is invalid.");
        }
        int products = (plan == null ? 0 : 1) + (addOn == null ? 0 : 1)
                + (quotaPackage == null ? 0 : 1);
        if (productType == null ? products != 0 : products != 1 || !productMatchesType()) {
            throw new IllegalStateException("Commercial policy effect product identity is invalid.");
        }
        if ((amount == null) != (currencyCode == null)
                || (maximumAmount == null) != (maximumCurrencyCode == null)) {
            throw new IllegalStateException("Commercial policy effect money is incomplete.");
        }
        if (amount != null) {
            Money normalized = Money.of(amount, currencyCode);
            amount = normalized.amount();
            currencyCode = normalized.currencyCode();
        }
        if (maximumAmount != null) {
            Money normalized = Money.of(maximumAmount, maximumCurrencyCode);
            maximumAmount = normalized.amount();
            maximumCurrencyCode = normalized.currencyCode();
        }
        quotaResource = normalize(quotaResource);
    }

    private boolean productMatchesType() {
        if (productType == null) return true;
        return switch (productType) {
            case PLAN -> plan != null;
            case ADD_ON -> addOn != null;
            case QUOTA_PACKAGE -> quotaPackage != null;
        };
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
