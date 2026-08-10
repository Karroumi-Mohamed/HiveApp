package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;

import java.math.BigDecimal;
import java.util.List;

public record SubscriptionAddOnSnapshot(
        String code,
        String name,
        long definitionVersion,
        BigDecimal price,
        String currencyCode,
        BillingCycle billingCycle,
        List<String> featureCodes
) {}
