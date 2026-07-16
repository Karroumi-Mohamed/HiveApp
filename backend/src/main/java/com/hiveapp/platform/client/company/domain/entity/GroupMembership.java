package com.hiveapp.platform.client.company.domain.entity;

import com.hiveapp.platform.client.member.domain.entity.Member;
import com.hiveapp.shared.domain.BaseEntity;
import com.hiveapp.shared.domain.TenantInvariant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "group_memberships", uniqueConstraints = @UniqueConstraint(
        name = "uk_group_membership_group_member", columnNames = {"group_id", "member_id"}))
@Getter @Setter
public class GroupMembership extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private OrganizationGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "position_title", length = 160)
    private String positionTitle;

    @PrePersist
    @PreUpdate
    void validateInvariant() {
        TenantInvariant.requireSameEntity(
                group.getCompany().getAccount(), member.getAccount(),
                "Group member must belong to the company account");
    }
}
