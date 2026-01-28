package com.hiveapp.plan.domain.entity;

import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "plan_features")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanFeature extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "feature_id", nullable = false)
    private UUID featureId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> config = new HashMap<>();
}
