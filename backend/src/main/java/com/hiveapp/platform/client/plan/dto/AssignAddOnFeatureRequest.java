package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.shared.quota.QuotaLimitEntry;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record AssignAddOnFeatureRequest(
        @NotBlank String featureCode,
        List<QuotaLimitEntry> quotaConfigs
) {}
