package com.hiveapp.platform.registry.domain.entity;

import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.shared.domain.BaseEntity;
import com.hiveapp.shared.quota.QuotaSlot;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.List;
import java.util.ArrayList;

/**
 * A marketable capability unit in the platform catalog.
 * Contains one or more Permissions (Bricks) and optionally a quota schema.
 *
 * quota_schema: defines the SHAPE of quotas for this feature (type + unit per resource).
 *   - Seeded/overwritten at startup by FeatureSeeder from AppFeature enum declarations.
 *   - Admin never touches this — code is the source of truth.
 *   - The actual limit VALUES live in PlanFeature.quota_configs, set by admin per plan.
 *
 * Display name and description are managed by frontend code, not stored in DB.
 */
@Entity
@Table(name = "features")
@Getter @Setter
public class Feature extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id", nullable = false)
    private Module module;

    @OneToMany(mappedBy = "feature", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Permission> permissions = new ArrayList<>();

    @Column(nullable = false, unique = true)
    private String code; // e.g. "hr.employees"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeatureStatus status = FeatureStatus.INTERNAL;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quota_schema")
    private List<QuotaSlot> quotaSchema = new ArrayList<>();

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
