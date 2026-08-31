package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Client-safe entitlement facts for one side of a subscription-change review.
 *
 * <p>The immutable commercial snapshot remains internal because it also contains exact price
 * identities and policy and Offer provenance.</p>
 */
public record ClientSubscriptionEntitlementState(
        String planCode,
        BillingCycle billingCycle,
        Set<String> featureCodes,
        List<EffectiveQuotaLimit> effectiveQuotaLimits,
        Set<String> addOnCodes,
        List<QuotaPackageSelection> quotaPackages
) {
    public ClientSubscriptionEntitlementState {
        featureCodes = immutableOrderedSet(featureCodes);
        effectiveQuotaLimits = effectiveQuotaLimits == null
                ? List.of() : List.copyOf(effectiveQuotaLimits);
        addOnCodes = immutableOrderedSet(addOnCodes);
        quotaPackages = quotaPackages == null ? List.of() : List.copyOf(quotaPackages);
    }

    private static <T> Set<T> immutableOrderedSet(Set<T> values) {
        return values == null || values.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }
}
