package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.shared.money.ExactDecimal;

import java.math.BigDecimal;
import java.util.UUID;

public record SubscriptionQuotaPackageSnapshot(
        String code,
        String name,
        long definitionVersion,
        String featureCode,
        String resource,
        long capacityPerUnit,
        int quantity,
        @ExactDecimal BigDecimal unitPrice,
        String currencyCode,
        BillingCycle billingCycle,
        UUID priceEntryId
) {
    public SubscriptionQuotaPackageSnapshot(
            String code, String name, long definitionVersion, String featureCode, String resource,
            long capacityPerUnit, int quantity, BigDecimal unitPrice, String currencyCode,
            BillingCycle billingCycle
    ) {
        this(code, name, definitionVersion, featureCode, resource, capacityPerUnit, quantity,
                unitPrice, currencyCode, billingCycle, null);
    }

    public long purchasedCapacity() {
        return Math.multiplyExact(capacityPerUnit, quantity);
    }
}
