package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.shared.quota.QuotaLimitMode;

public record EffectiveQuotaLimit(
        String featureCode,
        String resource,
        QuotaLimitMode mode,
        Long includedLimit,
        long purchasedCapacity,
        Long effectiveLimit
) {}
