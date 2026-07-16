package com.hiveapp.platform.admin.domain.entity;

import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "admin_user_roles", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_admin_user_roles_user_role",
                columnNames = {"admin_user_id", "admin_role_id"})
})
@Getter @Setter
public class AdminUserRole extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_user_id", nullable = false)
    private AdminUser adminUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_role_id", nullable = false)
    private AdminRole adminRole;
}
