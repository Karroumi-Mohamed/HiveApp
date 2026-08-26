package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;

import java.util.UUID;

/** Exact price ID wins; currency/cycle selection is the stable human-facing alternative. */
public record ProductPriceSelectionRequest(
        UUID priceEntryId,
        String currencyCode,
        BillingCycle billingCycle
) {}
