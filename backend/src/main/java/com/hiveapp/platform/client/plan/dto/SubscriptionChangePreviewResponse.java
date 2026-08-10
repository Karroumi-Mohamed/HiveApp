package com.hiveapp.platform.client.plan.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public record SubscriptionChangePreviewResponse(
        String currentPlanCode,
        String targetPlanCode,
        BigDecimal currentPrice,
        BigDecimal previewPrice,
        String currencyCode,
        boolean immediateAllowed,
        Set<String> effectiveFeatureCodes,
        List<EffectiveQuotaLimit> effectiveQuotaLimits,
        Set<String> addOnCodes,
        List<QuotaPackageSelection> quotaPackages,
        List<SubscriptionChangeConflict> conflicts
) {}
