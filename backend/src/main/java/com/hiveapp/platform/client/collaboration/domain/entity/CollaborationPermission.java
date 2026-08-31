package com.hiveapp.platform.client.collaboration.domain.entity;

import com.hiveapp.platform.registry.domain.entity.Permission;
import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "collaboration_permissions", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_collaboration_permissions_pair",
                columnNames = {"collaboration_id", "permission_id"})
})
@Getter @Setter
public class CollaborationPermission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collaboration_id", nullable = false)
    private Collaboration collaboration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "granted_by_user_id", nullable = false)
    private UUID grantedByUserId;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by_user_id")
    private UUID revokedByUserId;

    @Version
    @Column(nullable = false)
    private long version;
}
