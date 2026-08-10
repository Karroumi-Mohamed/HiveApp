package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.shared.quota.QuotaLimitEntry;
import com.hiveapp.shared.quota.QuotaOverride;

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
        List<QuotaLimitEntry> effectiveQuotaLimits,
        Set<String> addOnCodes,
        List<QuotaOverride> quotaOverrides,
        List<SubscriptionChangeConflict> conflicts
) {}
