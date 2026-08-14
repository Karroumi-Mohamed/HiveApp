package com.hiveapp.platform.admin.domain.entity;

import com.hiveapp.platform.admin.domain.constant.AdminRoleStatus;
import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "admin_roles", uniqueConstraints = {
        @UniqueConstraint(name = "uk_admin_roles_normalized_name", columnNames = "normalized_name")
})
@Getter @Setter
public class AdminRole extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 100)
    private String normalizedName;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdminRoleStatus status = AdminRoleStatus.INACTIVE;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "permission_revision", nullable = false)
    private long permissionRevision;

    @Column(name = "assignment_revision", nullable = false)
    private long assignmentRevision;

    @Column(name = "ever_assigned", nullable = false)
    private boolean everAssigned;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "updated_by_user_id")
    private UUID updatedByUserId;

    public boolean isActive() {
        return status == AdminRoleStatus.ACTIVE;
    }

    /** Compatibility for existing service code while lifecycle callers move to explicit status. */
    public void setActive(boolean active) {
        status = active ? AdminRoleStatus.ACTIVE : AdminRoleStatus.INACTIVE;
    }
}
