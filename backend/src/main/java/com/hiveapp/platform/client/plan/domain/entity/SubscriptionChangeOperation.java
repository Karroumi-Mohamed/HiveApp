package com.hiveapp.platform.client.plan.domain.entity;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeTiming;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrides;
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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscription_change_operations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_subscription_change_pending_account", columnNames = "pending_account_id")
})
@Getter
@Setter
public class SubscriptionChangeOperation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_subscription_id", nullable = false)
    private Subscription sourceSubscription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_plan_id", nullable = false)
    private Plan targetPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_subscription_id")
    private Subscription resultSubscription;

    @OneToOne(mappedBy = "changeOperation", fetch = FetchType.LAZY)
    private SubscriptionCheckout checkout;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionChangeTiming timing;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private SubscriptionChangeStatus status;

    @Column(name = "pending_account_id")
    private UUID pendingAccountId;

    @Column(name = "effective_at", nullable = false)
    private Instant effectiveAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requested_selection", nullable = false)
    private SubscriptionOverrides requestedSelection;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_snapshot", nullable = false)
    private SubscriptionEntitlementSnapshot beforeSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_snapshot", nullable = false)
    private SubscriptionEntitlementSnapshot targetSnapshot;

    @Column(name = "attention_reason", length = 2000)
    private String attentionReason;

    @Version
    @Column(nullable = false)
    private long version;

    @PrePersist
    @PreUpdate
    void validateOperation() {
        pendingAccountId = (status == SubscriptionChangeStatus.PENDING
                || status == SubscriptionChangeStatus.AWAITING_CONFIRMATION) && account != null
                ? account.getId()
                : null;
        if (requestedSelection == null || beforeSnapshot == null || targetSnapshot == null) {
            throw new IllegalStateException("Subscription change operation requires typed selection and snapshots");
        }
        if (effectiveAt == null || timing == null || status == null) {
            throw new IllegalStateException("Subscription change timing, status, and effective time are required");
        }
    }
}
