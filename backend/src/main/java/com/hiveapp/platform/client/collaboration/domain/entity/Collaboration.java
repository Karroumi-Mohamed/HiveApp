package com.hiveapp.platform.client.collaboration.domain.entity;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.company.domain.entity.Company;
import com.hiveapp.platform.client.collaboration.domain.constant.CollaborationStatus;
import com.hiveapp.shared.domain.BaseEntity;
import com.hiveapp.shared.domain.TenantInvariant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "collaborations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_collaboration_live_tuple", columnNames = "live_tuple_key")
})
@Getter @Setter
public class Collaboration extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_account_id", nullable = false)
    private Account clientAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_account_id", nullable = false)
    private Account providerAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CollaborationStatus status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "requested_by_user_id", nullable = false)
    private UUID requestedByUserId;

    @Column(nullable = false, length = 1000)
    private String purpose;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "collaboration_requested_permissions",
            joinColumns = @JoinColumn(name = "collaboration_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_collaboration_requested_permission",
                    columnNames = {"collaboration_id", "permission_code"}))
    @Column(name = "permission_code", nullable = false, length = 255)
    private Set<String> requestedPermissionCodes = new LinkedHashSet<>();

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "accepted_by_user_id")
    private UUID acceptedByUserId;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "rejected_by_user_id")
    private UUID rejectedByUserId;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by_user_id")
    private UUID cancelledByUserId;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "suspended_by_user_id")
    private UUID suspendedByUserId;

    @Column(name = "suspension_review_at")
    private Instant suspensionReviewAt;

    @Column(name = "automatic_resume_at")
    private Instant automaticResumeAt;

    @Column(name = "resumed_at")
    private Instant resumedAt;

    @Column(name = "resumed_by_user_id")
    private UUID resumedByUserId;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by_user_id")
    private UUID revokedByUserId;

    @Column(name = "lifecycle_reason", length = 1000)
    private String lifecycleReason;

    @Column(name = "live_tuple_key", length = 110)
    private String liveTupleKey;

    @Version
    @Column(nullable = false)
    private long version;

    @PrePersist
    @PreUpdate
    void validateTenantInvariant() {
        TenantInvariant.requireSameEntity(
                providerAccount,
                company.getAccount(),
                "Collaboration company must belong to the provider account");
        TenantInvariant.requireDifferentEntities(
                clientAccount,
                providerAccount,
                "Collaboration client and provider accounts must be different");
        liveTupleKey = status == null ? null : switch (status) {
            case PENDING, ACTIVE, SUSPENDED -> clientAccount.getId() + "|"
                    + providerAccount.getId() + "|" + company.getId();
            case CANCELLED, REJECTED, REVOKED -> null;
        };
    }
}
