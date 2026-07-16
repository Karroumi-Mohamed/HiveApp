package com.hiveapp.platform.client.member.domain.entity;

import com.hiveapp.platform.client.account.domain.entity.Company;
import com.hiveapp.platform.registry.domain.entity.Permission;
import com.hiveapp.shared.domain.BaseEntity;
import com.hiveapp.shared.domain.TenantInvariant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "member_permission_overrides", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_member_permission_overrides_scope",
                columnNames = {"member_id", "company_id", "permission_id"})
})
@Getter @Setter
public class MemberPermissionOverride extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;

    @Column(nullable = false)
    private boolean decision;

    @PrePersist
    @PreUpdate
    void validateTenantInvariant() {
        TenantInvariant.requireSameEntity(
                member.getAccount(),
                company.getAccount(),
                "Member permission override company must belong to the member account");
    }
}
