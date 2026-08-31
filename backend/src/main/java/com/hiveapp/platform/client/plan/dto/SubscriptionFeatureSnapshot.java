package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.shared.quota.QuotaLimitEntry;

import java.util.List;

public record SubscriptionFeatureSnapshot(
        String featureCode,
        List<QuotaLimitEntry> quotaConfigs
) {
}
