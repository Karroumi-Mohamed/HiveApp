package com.hiveapp.platform.admin.domain.entity;

import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "admin_role_permissions")
@Getter @Setter
public class AdminRolePermission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_role_id", nullable = false)
    private AdminRole adminRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_permission_id", nullable = false)
    private AdminPermission adminPermission; // Corrected: Links to AdminPermission
}
