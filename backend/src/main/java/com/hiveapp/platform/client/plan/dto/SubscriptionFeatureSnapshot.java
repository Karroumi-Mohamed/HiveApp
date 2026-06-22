package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.shared.quota.QuotaLimitEntry;

import java.math.BigDecimal;
import java.util.List;

public record SubscriptionFeatureSnapshot(
        String featureCode,
        BigDecimal addOnPrice,
        List<QuotaLimitEntry> quotaConfigs
) {
}
