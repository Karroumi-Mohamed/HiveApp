package com.hiveapp.platform.admin.domain.entity;

import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "admin_users")
@Getter @Setter
public class AdminUser extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "is_super_admin", nullable = false)
    private boolean isSuperAdmin = false;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
