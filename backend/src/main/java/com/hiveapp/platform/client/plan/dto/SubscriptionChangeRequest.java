package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.shared.quota.QuotaOverride;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Set;

public record SubscriptionChangeRequest(
        @NotBlank String targetPlanCode,
        Set<String> addOnFeatureCodes,
        List<QuotaOverride> quotaOverrides
) {}
