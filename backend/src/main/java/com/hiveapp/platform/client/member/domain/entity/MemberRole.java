package com.hiveapp.platform.client.member.domain.entity;

import com.hiveapp.platform.client.account.domain.entity.Company;
import com.hiveapp.platform.client.role.domain.entity.Role;
import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "member_roles")
@Getter @Setter
public class MemberRole extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company; // Null = Account-wide, Value = Company-scoped
}
