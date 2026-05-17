package com.hiveapp.platform.registry.dto;

import com.hiveapp.platform.registry.definition.FeatureSurface;
import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.shared.quota.QuotaSlot;

import java.util.List;
import java.util.UUID;

public record RegistryFeatureReadModelDto(
        UUID id,
        String code,
        String moduleCode,
        String featureKey,
        String displayName,
        String description,
        FeatureSurface surface,
        FeatureStatus status,
        boolean active,
        boolean registryPresent,
        boolean planAssignable,
        boolean clientRoleGrantable,
        boolean platformAdminRoleGrantable,
        boolean b2bDelegatable,
        boolean publicCatalogVisible,
        int sortOrder,
        List<QuotaSlot> quotaSchema,
        List<RegistryPermissionDto> permissions
) {
}
