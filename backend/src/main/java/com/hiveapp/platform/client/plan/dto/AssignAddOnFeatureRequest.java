package com.hiveapp.platform.client.plan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AssignAddOnFeatureRequest(
        @NotBlank @Size(max = 160) String featureCode,
        @Valid List<QuotaLimitRequest> quotaConfigs
) {
    public List<com.hiveapp.shared.quota.QuotaLimitEntry> quotaEntries() {
        return quotaConfigs == null
                ? List.of()
                : quotaConfigs.stream().map(QuotaLimitRequest::toEntry).toList();
    }
}
