package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.shared.money.ExactDecimal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Client projection of a signed review; policy targeting and immutable provenance stay internal. */
public record ClientSubscriptionChangePreviewResponse(
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
        List<SubscriptionChangeConflict> conflicts,
        ClientCommercialPolicyEvaluation commercialPolicyEvaluation
) {
    public static ClientSubscriptionChangePreviewResponse from(
            SubscriptionChangePreviewResponse source
    ) {
        return new ClientSubscriptionChangePreviewResponse(
                source.subscriptionId(), source.expectedSubscriptionVersion(), source.catalogRevision(),
                source.registryVersion(), source.evaluatedAt(), source.expiresAt(), source.previewToken(),
                source.currentPlanCode(), source.targetPlanCode(), source.currentPrice(),
                source.previewPrice(), source.currencyCode(), source.immediateAllowed(),
                source.effectiveFeatureCodes(), source.effectiveQuotaLimits(), source.addOnCodes(),
                source.quotaPackages(), source.conflicts(),
                ClientCommercialPolicyEvaluation.from(source.commercialPolicyEvaluation()));
    }
}
