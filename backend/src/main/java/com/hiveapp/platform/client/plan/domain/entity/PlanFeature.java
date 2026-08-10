package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import com.hiveapp.shared.domain.BaseEntity;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * Links a Feature to a Plan and stores the admin-configured quota limit values for that plan tier.
 *
 * mode         — INCLUDED grants the feature in the base Plan; OPTIONAL_ADD_ON permits a
 *                compatible AddOn to grant it; BLOCKED_FOR_PLAN explicitly prevents it.
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PlanFeatureMode mode = PlanFeatureMode.INCLUDED;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quota_configs")
    private List<QuotaLimitEntry> quotaConfigs = new ArrayList<>();

    @PrePersist
    @PreUpdate
    void validateConfiguration() {
        if (mode == null) {
            throw new IllegalStateException("Plan feature mode is required");
        }
        if (mode != PlanFeatureMode.INCLUDED && quotaConfigs != null && !quotaConfigs.isEmpty()) {
            throw new IllegalStateException("Only included Plan features may define base quota limits");
        }
        if (quotaConfigs != null) {
            quotaConfigs.stream()
                    .filter(entry -> entry.pricePerUnit() != null)
                    .forEach(entry -> plan.money().requireSameCurrency(entry.priceMoney()));
        }
    }
}
