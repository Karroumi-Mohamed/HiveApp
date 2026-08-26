package com.hiveapp.platform.client.plan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeTiming;

import java.util.List;
import java.util.Set;

public record SubscriptionChangeRequest(
        @NotBlank String targetPlanCode,
        Set<String> addOnCodes,
        @Valid List<QuotaPackageSelection> quotaPackages,
        SubscriptionChangeTiming timing,
        @Valid ProductPriceSelectionRequest planPriceSelection
) {
    public SubscriptionChangeRequest(
            String targetPlanCode,
            Set<String> addOnCodes,
            List<QuotaPackageSelection> quotaPackages
    ) {
        this(targetPlanCode, addOnCodes, quotaPackages, SubscriptionChangeTiming.IMMEDIATE, null);
    }

    public SubscriptionChangeRequest(
            String targetPlanCode,
            Set<String> addOnCodes,
            List<QuotaPackageSelection> quotaPackages,
            SubscriptionChangeTiming timing
    ) {
        this(targetPlanCode, addOnCodes, quotaPackages, timing, null);
    }

    public SubscriptionChangeTiming effectiveTiming() {
        return timing == null ? SubscriptionChangeTiming.IMMEDIATE : timing;
    }
}
