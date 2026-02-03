package com.hiveapp.admin.domain.entity;

import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "admin_roles")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminRole extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    private String description;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @OneToMany(mappedBy = "adminRole", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AdminRolePermission> adminRolePermissions = new ArrayList<>();

    public void addPermission(AdminRolePermission permission) {
        adminRolePermissions.add(permission);
        permission.setAdminRole(this);
    }

    public void removePermission(AdminRolePermission permission) {
        adminRolePermissions.remove(permission);
        permission.setAdminRole(null);
    }

    public void clearPermissions() {
        adminRolePermissions.clear();
    }

    public void activate() {
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }
}
