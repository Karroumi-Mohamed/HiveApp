package com.hiveapp.member.domain.entity;

import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "member_roles")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberRole extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "assigned_at", nullable = false)
    @Builder.Default
    private Instant assignedAt = Instant.now();

    public boolean isAccountWide() {
        return companyId == null;
    }

    public boolean isCompanyScoped() {
        return companyId != null;
    }
}
