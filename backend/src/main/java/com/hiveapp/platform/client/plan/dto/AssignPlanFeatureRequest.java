package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.shared.quota.QuotaLimitEntry;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;

public record AssignPlanFeatureRequest(
        @NotBlank String featureCode,
        BigDecimal addOnPrice,
        List<QuotaLimitEntry> quotaConfigs
) {}
