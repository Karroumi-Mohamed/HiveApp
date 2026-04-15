package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.shared.domain.BaseEntity;
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
@Table(name = "plan_features")
@Getter @Setter
public class PlanFeature extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feature_id", nullable = false)
    private Feature feature;

    @Column(name = "add_on_price", precision = 10, scale = 2)
    private BigDecimal addOnPrice;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quota_configs")
    private List<QuotaLimitEntry> quotaConfigs = new ArrayList<>();
}
