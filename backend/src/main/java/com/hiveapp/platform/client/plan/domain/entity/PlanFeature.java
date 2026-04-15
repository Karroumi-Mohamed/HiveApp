package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.registry.domain.entity.Feature;
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
 * quota_configs: one entry per quota slot declared in Feature.quota_schema.
 *   resource must match a resource name in the Feature's QuotaSlot list.
 *   null limit = explicitly unlimited for this plan tier.
 *   Empty list = feature is included in the plan but has no quota (boolean access).
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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quota_configs")
    private List<QuotaLimitEntry> quotaConfigs = new ArrayList<>();
}
