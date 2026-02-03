package com.hiveapp.admin.domain.entity;

import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "admin_role_permissions")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminRolePermission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_role_id", nullable = false)
    private AdminRole adminRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_permission_id", nullable = false)
    private AdminPermission adminPermission;

    @Column(name = "granted_at", nullable = false)
    @Builder.Default
    private Instant grantedAt = Instant.now();
}
