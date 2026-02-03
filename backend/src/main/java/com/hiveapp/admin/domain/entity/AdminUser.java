package com.hiveapp.admin.domain.entity;

import com.hiveapp.permission.engine.IPermissionActor;
import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "admin_users")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUser extends BaseEntity implements IPermissionActor {

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "is_super_admin", nullable = false)
    @Builder.Default
    private boolean isSuperAdmin = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @OneToMany(mappedBy = "adminUser", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AdminUserRole> adminUserRoles = new ArrayList<>();

    public void activate() {
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public void addRole(AdminUserRole role) {
        adminUserRoles.add(role);
        role.setAdminUser(this);
    }

    public void removeRole(AdminUserRole role) {
        adminUserRoles.remove(role);
        role.setAdminUser(null);
    }

    // --- IPermissionActor ---

    @Override
    public UUID getActorAccountId() {
        return null; // Admin platform has no account scope
    }

    @Override
    public boolean isActorActive() {
        return this.isActive;
    }
}
