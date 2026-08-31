package com.hiveapp.platform.client.member.domain.entity;

import com.hiveapp.platform.client.company.domain.entity.Company;
import com.hiveapp.platform.client.member.domain.constant.PermissionOverrideDecision;
import com.hiveapp.platform.client.member.domain.constant.PermissionOverrideScope;
import com.hiveapp.platform.registry.domain.entity.Permission;
import com.hiveapp.shared.domain.BaseEntity;
import com.hiveapp.shared.domain.TenantInvariant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "member_permission_overrides", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_member_permission_overrides_scope",
                columnNames = {"member_id", "scope_key", "permission_id"})
})
@Getter @Setter
public class MemberPermissionOverride extends BaseEntity {

    private static final UUID ACCOUNT_SCOPE_KEY = new UUID(0L, 0L);

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PermissionOverrideScope scope = PermissionOverrideScope.ACCOUNT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scope_company_id")
    private Company scopeCompany;

    @Column(name = "scope_key", nullable = false, updatable = false)
    private UUID scopeKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PermissionOverrideDecision decision;

    @Column(nullable = false, length = 500)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_member_id", nullable = false, updatable = false)
    private Member createdBy;

    @Column(name = "expires_at")
    private Instant expiresAt;

    public boolean isEffectiveAt(Instant instant) {
        return expiresAt == null || expiresAt.isAfter(instant);
    }

    @PrePersist
    @PreUpdate
    void validateTenantInvariant() {
        if (member == null) {
            throw new IllegalStateException("Permission exceptions require a member");
        }
        if (scope == null) {
            throw new IllegalStateException("Permission exceptions require a scope");
        }
        if (decision == null) {
            throw new IllegalStateException("Permission exceptions require a decision");
        }
        if (createdBy == null) {
            throw new IllegalStateException("Permission exceptions require a creator");
        }
        if (scope == PermissionOverrideScope.ACCOUNT && scopeCompany != null) {
            throw new IllegalStateException("Account permission exceptions cannot declare a scope company");
        }
        if (scope == PermissionOverrideScope.COMPANY && scopeCompany == null) {
            throw new IllegalStateException("Company permission exceptions require a scope company");
        }
        if (scopeCompany != null) {
            TenantInvariant.requireSameEntity(
                    member.getAccount(),
                    scopeCompany.getAccount(),
                    "Member permission exception company must belong to the member account");
        }
        TenantInvariant.requireSameEntity(
                member.getAccount(),
                createdBy.getAccount(),
                "Permission exception creator must belong to the member account");
        if (reason == null || reason.isBlank()) {
            throw new IllegalStateException("Permission exceptions require a reason");
        }
        if (reason.length() > 500) {
            throw new IllegalStateException("Permission exception reasons cannot exceed 500 characters");
        }
        if (decision == PermissionOverrideDecision.GRANT && expiresAt == null) {
            throw new IllegalStateException("Permission GRANT exceptions require an expiry");
        }
        scopeKey = scope == PermissionOverrideScope.ACCOUNT ? ACCOUNT_SCOPE_KEY : scopeCompany.getId();
    }
}
