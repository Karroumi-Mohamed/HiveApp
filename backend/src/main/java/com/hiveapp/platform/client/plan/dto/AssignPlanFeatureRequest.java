package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AssignPlanFeatureRequest(
        @NotBlank @Size(max = 160) String featureCode,
        @NotNull PlanFeatureMode mode,
        @Valid List<QuotaLimitRequest> quotaConfigs
) {
    public List<com.hiveapp.shared.quota.QuotaLimitEntry> quotaEntries() {
        return quotaConfigs == null
                ? List.of()
                : quotaConfigs.stream().map(QuotaLimitRequest::toEntry).toList();
    }
}
