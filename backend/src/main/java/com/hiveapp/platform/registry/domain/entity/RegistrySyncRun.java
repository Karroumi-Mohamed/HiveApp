package com.hiveapp.platform.registry.domain.entity;

import com.hiveapp.platform.registry.domain.constant.RegistrySyncStatus;
import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "registry_sync_runs")
@Getter
@Setter
public class RegistrySyncRun extends BaseEntity {

    @Column(name = "build_version", nullable = false, length = 120)
    private String buildVersion;

    @Column(name = "snapshot_hash", length = 64)
    private String snapshotHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegistrySyncStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @Column(name = "discovered_modules", nullable = false)
    private int discoveredModules;

    @Column(name = "discovered_features", nullable = false)
    private int discoveredFeatures;

    @Column(name = "discovered_permissions", nullable = false)
    private int discoveredPermissions;

    @Column(name = "created_modules", nullable = false)
    private int createdModules;

    @Column(name = "created_features", nullable = false)
    private int createdFeatures;

    @Column(name = "updated_features", nullable = false)
    private int updatedFeatures;

    @Column(name = "created_permissions", nullable = false)
    private int createdPermissions;

    @Column(name = "updated_permissions", nullable = false)
    private int updatedPermissions;

    @Column(name = "orphaned_permissions", nullable = false)
    private int orphanedPermissions;

    @Column(length = 2000)
    private String details;
}
