package com.hiveapp.admin.domain.entity;

import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "admin_user_roles")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUserRole extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_user_id", nullable = false)
    private AdminUser adminUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_role_id", nullable = false)
    private AdminRole adminRole;

    @Column(name = "assigned_at", nullable = false)
    @Builder.Default
    private Instant assignedAt = Instant.now();
}
