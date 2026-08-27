package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.shared.money.ExactDecimal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record SubscriptionChangePreviewResponse(
        UUID subscriptionId,
        long expectedSubscriptionVersion,
        long catalogRevision,
        String registryVersion,
        Instant evaluatedAt,
        Instant expiresAt,
        String previewToken,
        String currentPlanCode,
        String targetPlanCode,
        @ExactDecimal BigDecimal currentPrice,
        @ExactDecimal BigDecimal previewPrice,
        String currencyCode,
        boolean immediateAllowed,
        Set<String> effectiveFeatureCodes,
        List<EffectiveQuotaLimit> effectiveQuotaLimits,
        Set<String> addOnCodes,
        List<QuotaPackageSelection> quotaPackages,
        List<SubscriptionChangeConflict> conflicts
) {}
