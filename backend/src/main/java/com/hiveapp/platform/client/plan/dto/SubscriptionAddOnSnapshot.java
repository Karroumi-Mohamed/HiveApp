package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.shared.money.ExactDecimal;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SubscriptionAddOnSnapshot(
        String code,
        String name,
        long definitionVersion,
        @ExactDecimal BigDecimal price,
        String currencyCode,
        BillingCycle billingCycle,
        List<String> featureCodes,
        UUID priceEntryId
) {
    public SubscriptionAddOnSnapshot(
            String code, String name, long definitionVersion, BigDecimal price,
            String currencyCode, BillingCycle billingCycle, List<String> featureCodes
    ) {
        this(code, name, definitionVersion, price, currencyCode, billingCycle, featureCodes, null);
    }
}
