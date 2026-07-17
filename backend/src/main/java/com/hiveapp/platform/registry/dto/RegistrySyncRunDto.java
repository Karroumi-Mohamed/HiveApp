package com.hiveapp.platform.registry.dto;

import com.hiveapp.platform.registry.domain.constant.RegistrySyncStatus;

import java.time.Instant;
import java.util.UUID;

public record RegistrySyncRunDto(
        UUID id,
        String buildVersion,
        String snapshotHash,
        RegistrySyncStatus status,
        Instant startedAt,
        Instant completedAt,
        int discoveredModules,
        int discoveredFeatures,
        int discoveredPermissions,
        int createdModules,
        int createdFeatures,
        int updatedFeatures,
        int createdPermissions,
        int updatedPermissions,
        int orphanedPermissions,
        String details
) {
}
