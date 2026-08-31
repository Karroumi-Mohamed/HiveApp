package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.shared.quota.QuotaLimitEntry;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;

import java.util.List;
import java.util.UUID;

public record PlanFeatureDto(
        UUID id,
        String featureCode,
        PlanFeatureMode mode,
        List<QuotaLimitEntry> quotaConfigs
) {}
