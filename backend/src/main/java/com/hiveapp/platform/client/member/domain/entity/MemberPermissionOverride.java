package com.hiveapp.platform.client.member.domain.entity;

import com.hiveapp.platform.client.account.domain.entity.Company;
import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "member_permission_overrides")
@Getter @Setter
public class MemberPermissionOverride extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "permission_code", nullable = false)
    private String permissionCode;

    @Column(nullable = false)
    private boolean decision; // true = ALLOW, false = DENY
}
