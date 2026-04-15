package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.shared.quota.QuotaLimitEntry;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PlanFeatureDto(
        UUID id,
        String featureCode,
        BigDecimal addOnPrice,
        List<QuotaLimitEntry> quotaConfigs
) {}
