package com.hiveapp.platform.registry.domain.entity;

import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "registry_sync_locks")
@Getter
@Setter
public class RegistrySyncLock extends BaseEntity {

    @Column(name = "lock_name", nullable = false, unique = true, updatable = false)
    private String lockName;

    @Column(name = "last_snapshot_hash", length = 64)
    private String lastSnapshotHash;

    @Column(name = "last_build_version", length = 120)
    private String lastBuildVersion;

    @Column(name = "last_completed_at")
    private Instant lastCompletedAt;
}
