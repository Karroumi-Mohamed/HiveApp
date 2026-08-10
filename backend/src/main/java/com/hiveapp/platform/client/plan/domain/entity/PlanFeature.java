package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.shared.domain.BaseEntity;
import com.hiveapp.shared.money.Money;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Links a Feature to a Plan and stores the admin-configured quota limit values for that plan tier.
 *
 * addOnPrice   — monthly cost to add this feature to a subscription beyond the base plan.
 *                null = feature is included in the plan (not available as a standalone add-on).
 *
 * quotaConfigs — one entry per quota slot declared in Feature.quota_schema.
 *                resource must match a resource name in the Feature's QuotaSlot list.
 *                null limit = explicitly unlimited for this plan tier.
 *                pricePerUnit on each entry = cost per unit if client bumps beyond this limit.
 *                Empty list = feature has boolean access (no quota).
 */
@Entity
@Table(name = "plan_features", uniqueConstraints = {
        @UniqueConstraint(name = "uk_plan_features_plan_feature", columnNames = {"plan_id", "feature_id"})
})
@Getter @Setter
public class PlanFeature extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feature_id", nullable = false)
    private Feature feature;

    @Column(name = "add_on_price", precision = 19, scale = 4)
    private BigDecimal addOnPrice;

    @Column(name = "add_on_currency_code", length = 3)
    private String addOnCurrencyCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quota_configs")
    private List<QuotaLimitEntry> quotaConfigs = new ArrayList<>();

    public Money addOnMoney() {
        return addOnPrice == null ? null : Money.of(addOnPrice, addOnCurrencyCode);
    }

    public void setAddOnMoney(Money money) {
        addOnPrice = money != null ? money.amount() : null;
        addOnCurrencyCode = money != null ? money.currencyCode() : null;
    }

    @PrePersist
    @PreUpdate
    void validatePriceCurrencies() {
        if (addOnPrice != null) {
            Money addOn = Money.of(addOnPrice, addOnCurrencyCode);
            plan.money().requireSameCurrency(addOn);
            setAddOnMoney(addOn);
        } else {
            addOnCurrencyCode = null;
        }
        if (quotaConfigs != null) {
            quotaConfigs.stream()
                    .filter(entry -> entry.pricePerUnit() != null)
                    .forEach(entry -> plan.money().requireSameCurrency(entry.priceMoney()));
        }
    }
}
