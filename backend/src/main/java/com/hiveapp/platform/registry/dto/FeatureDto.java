package com.hiveapp.platform.registry.dto;

import java.util.List;
import java.util.UUID;
import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.shared.quota.QuotaSlot;

public record FeatureDto(
    UUID id, 
    String code, 
    FeatureStatus status, 
    List<QuotaSlot> quotaSchema
) {}
