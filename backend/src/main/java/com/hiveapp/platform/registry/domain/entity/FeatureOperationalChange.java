package com.hiveapp.platform.registry.domain.entity;

import com.hiveapp.platform.registry.domain.constant.FeatureOperationalControl;
import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "feature_operational_changes")
@Getter
@Setter
public class FeatureOperationalChange extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feature_id", nullable = false)
    private Feature feature;

    @Enumerated(EnumType.STRING)
    @Column(name = "control_type", nullable = false, length = 40)
    private FeatureOperationalControl control;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "previous_value", nullable = false)
    private boolean previousValue;

    @Column(name = "new_value", nullable = false)
    private boolean newValue;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "impact_confirmed", nullable = false)
    private boolean impactConfirmed;

    @Column(name = "communication_confirmed", nullable = false)
    private boolean communicationConfirmed;

    @Column(name = "effective_timing", nullable = false, length = 20)
    private String effectiveTiming = "IMMEDIATE";
}
