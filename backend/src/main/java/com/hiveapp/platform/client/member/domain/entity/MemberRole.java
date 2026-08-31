package com.hiveapp.platform.client.member.domain.entity;

import com.hiveapp.platform.client.company.domain.entity.Company;
import com.hiveapp.platform.client.member.domain.constant.RoleAssignmentScope;
import com.hiveapp.platform.client.role.domain.entity.Role;
import com.hiveapp.platform.client.role.domain.constant.RoleTemplateBoundary;
import com.hiveapp.shared.domain.BaseEntity;
import com.hiveapp.shared.domain.TenantInvariant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "member_roles", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_member_roles_member_role_scope",
                columnNames = {"member_id", "role_id", "scope_key"})
})
@Getter @Setter
public class MemberRole extends BaseEntity {

    private static final UUID ACCOUNT_SCOPE_KEY = new UUID(0L, 0L);

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "effect_scope", nullable = false, length = 20)
    private RoleAssignmentScope effectScope = RoleAssignmentScope.ACCOUNT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scope_company_id")
    private Company scopeCompany;

    @Column(name = "scope_key", nullable = false, updatable = false)
    private UUID scopeKey;

    @PrePersist
    @PreUpdate
    void validateTenantInvariant() {
        TenantInvariant.requireSameEntity(
                member.getAccount(),
                role.getAccount(),
                "Member and role must belong to the same account");
        if (effectScope == RoleAssignmentScope.ACCOUNT && scopeCompany != null) {
            throw new IllegalStateException("Account role assignments cannot declare a scope company");
        }
        if (effectScope == RoleAssignmentScope.COMPANY && scopeCompany == null) {
            throw new IllegalStateException("Company role assignments require a scope company");
        }
        if (scopeCompany != null) {
            TenantInvariant.requireSameEntity(
                    member.getAccount(),
                    scopeCompany.getAccount(),
                    "Member role company must belong to the member account");
        }
        if (role.getTemplateBoundary() == RoleTemplateBoundary.COMPANY) {
            TenantInvariant.requireSameEntity(
                    role.getBoundaryCompany(),
                    scopeCompany,
                    "A Company-bound role template must be assigned inside its boundary company");
        }
        scopeKey = effectScope == RoleAssignmentScope.ACCOUNT ? ACCOUNT_SCOPE_KEY : scopeCompany.getId();
    }
}
