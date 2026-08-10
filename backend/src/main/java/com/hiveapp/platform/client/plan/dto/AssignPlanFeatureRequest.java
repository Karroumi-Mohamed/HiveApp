package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.shared.quota.QuotaLimitEntry;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AssignPlanFeatureRequest(
        @NotBlank String featureCode,
        @NotNull PlanFeatureMode mode,
        List<QuotaLimitEntry> quotaConfigs
) {}
