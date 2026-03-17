package com.hiveapp.platform.client.collaboration.domain.entity;

import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "collaboration_permissions")
@Getter @Setter
public class CollaborationPermission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collaboration_id", nullable = false)
    private Collaboration collaboration;

    @Column(name = "permission_code", nullable = false)
    private String permissionCode;
}
