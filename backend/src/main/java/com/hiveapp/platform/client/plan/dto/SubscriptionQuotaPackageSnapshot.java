package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;

import java.math.BigDecimal;

public record SubscriptionQuotaPackageSnapshot(
        String code,
        String name,
        long definitionVersion,
        String featureCode,
        String resource,
        long capacityPerUnit,
        int quantity,
        BigDecimal unitPrice,
        String currencyCode,
        BillingCycle billingCycle
) {
    public long purchasedCapacity() {
        return Math.multiplyExact(capacityPerUnit, quantity);
    }
}
