package com.hiveapp.collaboration.domain.entity;

import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "collaborations")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Collaboration extends BaseEntity {

    @Column(name = "client_account_id", nullable = false)
    private UUID clientAccountId;

    @Column(name = "provider_account_id", nullable = false)
    private UUID providerAccountId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "pending";

    @Column(name = "requested_at", nullable = false)
    @Builder.Default
    private Instant requestedAt = Instant.now();

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @OneToMany(mappedBy = "collaboration", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CollaborationPermission> collaborationPermissions = new ArrayList<>();

    public void accept() {
        this.status = "active";
        this.acceptedAt = Instant.now();
    }

    public void suspend() {
        this.status = "suspended";
    }

    public void revoke() {
        this.status = "revoked";
        this.revokedAt = Instant.now();
    }

    public void reactivate() {
        this.status = "active";
        this.revokedAt = null;
    }

    public boolean isActive() {
        return "active".equals(this.status);
    }

    public boolean isPending() {
        return "pending".equals(this.status);
    }

    public boolean isRevoked() {
        return "revoked".equals(this.status);
    }

    public void addPermission(CollaborationPermission permission) {
        collaborationPermissions.add(permission);
        permission.setCollaboration(this);
    }

    public void removePermission(CollaborationPermission permission) {
        collaborationPermissions.remove(permission);
        permission.setCollaboration(null);
    }

    public void clearPermissions() {
        collaborationPermissions.clear();
    }
}
