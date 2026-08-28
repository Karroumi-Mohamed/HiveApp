package com.hiveapp.platform.client.plan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeTiming;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record SubscriptionChangeRequest(
        @NotBlank String targetPlanCode,
        Set<@NotBlank String> addOnCodes,
        @Valid List<QuotaPackageSelection> quotaPackages,
        SubscriptionChangeTiming timing,
        @Valid ProductPriceSelectionRequest planPriceSelection,
        Map<String, UUID> addOnPriceEntryIds,
        Map<String, UUID> quotaPackagePriceEntryIds
) {
    public SubscriptionChangeRequest {
        addOnPriceEntryIds = addOnPriceEntryIds == null ? Map.of() : Map.copyOf(addOnPriceEntryIds);
        quotaPackagePriceEntryIds = quotaPackagePriceEntryIds == null
                ? Map.of() : Map.copyOf(quotaPackagePriceEntryIds);
    }
    public SubscriptionChangeRequest(
            String targetPlanCode,
            Set<String> addOnCodes,
            List<QuotaPackageSelection> quotaPackages
    ) {
        this(targetPlanCode, addOnCodes, quotaPackages, SubscriptionChangeTiming.IMMEDIATE,
                null, Map.of(), Map.of());
    }

    public SubscriptionChangeRequest(
            String targetPlanCode,
            Set<String> addOnCodes,
            List<QuotaPackageSelection> quotaPackages,
            SubscriptionChangeTiming timing
    ) {
        this(targetPlanCode, addOnCodes, quotaPackages, timing, null, Map.of(), Map.of());
    }

    public SubscriptionChangeRequest(String targetPlanCode, Set<String> addOnCodes,
            List<QuotaPackageSelection> quotaPackages, SubscriptionChangeTiming timing,
            ProductPriceSelectionRequest planPriceSelection) {
        this(targetPlanCode, addOnCodes, quotaPackages, timing, planPriceSelection,
                Map.of(), Map.of());
    }

    public SubscriptionChangeTiming effectiveTiming() {
        return timing == null ? SubscriptionChangeTiming.IMMEDIATE : timing;
    }
}
