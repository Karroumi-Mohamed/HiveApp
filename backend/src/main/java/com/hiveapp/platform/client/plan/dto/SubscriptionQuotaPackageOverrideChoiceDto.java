package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.RetainedEntitlementState;
import com.hiveapp.shared.money.ExactDecimal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record SubscriptionQuotaPackageOverrideChoiceDto(
        UUID productId,
        String code,
        String name,
        String featureCode,
        String resource,
        long capacityPerUnit,
        boolean repeatable,
        int maximumQuantity,
        Integer retainedQuantity,
        Set<String> requiredAddOnCodes,
        UUID priceEntryId,
        @ExactDecimal BigDecimal unitPrice,
        String currencyCode,
        BillingCycle billingCycle,
        RetainedEntitlementState state,
        boolean retained,
        boolean removable,
        boolean quantityEditable,
        List<ExtensionAvailabilityIssue> unavailabilityReasons
) {
    public SubscriptionQuotaPackageOverrideChoiceDto {
        requiredAddOnCodes = Set.copyOf(requiredAddOnCodes == null ? Set.of() : requiredAddOnCodes);
        unavailabilityReasons = List.copyOf(
                unavailabilityReasons == null ? List.of() : unavailabilityReasons);
    }
}
