package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionPeriodStatus;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.shared.domain.BaseEntity;
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
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "subscription_periods")
@Getter
@Setter
public class SubscriptionPeriod extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private SubscriptionPeriodStatus status = SubscriptionPeriodStatus.OPEN;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "entitlement_snapshot", nullable = false)
    private SubscriptionEntitlementSnapshot entitlementSnapshot;

    @Column(name = "closed_at")
    private Instant closedAt;

    @PrePersist
    @PreUpdate
    void validatePeriod() {
        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw new IllegalStateException("Subscription period end must be after its start");
        }
        if (entitlementSnapshot == null) {
            throw new IllegalStateException("Subscription period entitlement snapshot is required");
        }
        if (status == SubscriptionPeriodStatus.OPEN && closedAt != null) {
            throw new IllegalStateException("An open subscription period cannot have a closed time");
        }
        if (status != SubscriptionPeriodStatus.OPEN && closedAt == null) {
            throw new IllegalStateException("A closed subscription period requires a closed time");
        }
    }
}
