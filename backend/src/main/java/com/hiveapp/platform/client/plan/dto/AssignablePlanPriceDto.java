package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Bounded subscription-assignment chooser; it never exposes non-Plan or non-sellable price rows. */
public record AssignablePlanPriceDto(
        UUID planId,
        String planCode,
        String planName,
        int planRevisionNumber,
        UUID priceEntryId,
        BigDecimal amount,
        String currencyCode,
        BillingCycle billingCycle,
        Instant effectiveFrom,
        Instant effectiveUntil
) {}
