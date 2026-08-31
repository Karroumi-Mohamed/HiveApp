package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionLifecycleAction;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Durable, operator-facing subscription lifecycle history. Old events are never rewritten. */
@Entity
@Table(name = "subscription_lifecycle_events", indexes = @Index(
        name = "idx_subscription_lifecycle_account_created_id",
        columnList = "account_id,created_at,id"))
@Getter
@Setter
public class SubscriptionLifecycleEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SubscriptionLifecycleAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "before_status", nullable = false, length = 24)
    private SubscriptionStatus beforeStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "after_status", nullable = false, length = 24)
    private SubscriptionStatus afterStatus;

    @Column(name = "effective_at", nullable = false)
    private Instant effectiveAt;

    @Column(name = "previous_grace_ends_at")
    private Instant previousGraceEndsAt;

    @Column(name = "next_grace_ends_at")
    private Instant nextGraceEndsAt;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(nullable = false, length = 2000)
    private String reason;
}
