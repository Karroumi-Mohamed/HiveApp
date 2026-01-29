package com.hiveapp.subscription.domain.entity;

import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription extends BaseEntity {

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "active";

    @Column(name = "current_period_start", nullable = false)
    @Builder.Default
    private Instant currentPeriodStart = Instant.now();

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    public boolean isActive() {
        return "active".equals(status);
    }

    public boolean isTrialing() {
        return "trialing".equals(status);
    }

    public boolean isCancelled() {
        return "cancelled".equals(status);
    }

    public void cancel() {
        this.status = "cancelled";
        this.cancelledAt = Instant.now();
    }

    public void activate() {
        this.status = "active";
        this.cancelledAt = null;
    }

    public void renew(Instant newPeriodStart, Instant newPeriodEnd) {
        this.status = "active";
        this.currentPeriodStart = newPeriodStart;
        this.currentPeriodEnd = newPeriodEnd;
    }

    public void changePlan(UUID newPlanId) {
        this.planId = newPlanId;
    }
}
