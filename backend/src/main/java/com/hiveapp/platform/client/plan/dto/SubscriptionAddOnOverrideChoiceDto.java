package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.RetainedEntitlementState;
import com.hiveapp.shared.money.ExactDecimal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record SubscriptionAddOnOverrideChoiceDto(
        UUID productId,
        String code,
        String name,
        Set<String> featureCodes,
        Set<String> requiredAddOnCodes,
        UUID priceEntryId,
        @ExactDecimal BigDecimal unitPrice,
        String currencyCode,
        BillingCycle billingCycle,
        RetainedEntitlementState state,
        boolean retained,
        boolean removable,
        List<ExtensionAvailabilityIssue> unavailabilityReasons
) {
    public SubscriptionAddOnOverrideChoiceDto {
        featureCodes = Set.copyOf(featureCodes == null ? Set.of() : featureCodes);
        requiredAddOnCodes = Set.copyOf(requiredAddOnCodes == null ? Set.of() : requiredAddOnCodes);
        unavailabilityReasons = List.copyOf(
                unavailabilityReasons == null ? List.of() : unavailabilityReasons);
    }
}
