package com.hiveapp.platform.registry.dto;

import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.shared.quota.QuotaSlot;

import java.util.List;

public record PublicFeatureCatalogFeatureDto(
        String code,
        String displayName,
        String description,
        FeatureStatus status,
        List<QuotaSlot> quotaSchema
) {
}
