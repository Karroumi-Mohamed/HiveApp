package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
    @Column(name = "config")
    private Object config;
}
