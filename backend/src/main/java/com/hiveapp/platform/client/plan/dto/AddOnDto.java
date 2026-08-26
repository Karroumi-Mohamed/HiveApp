package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.AddOnCreationReason;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.shared.quota.QuotaLimitEntry;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record AddOnDto(
        UUID id,
        String code,
        String name,
        String description,
        BigDecimal price,
        String currencyCode,
        BillingCycle billingCycle,
        AddOnStatus status,
        long definitionVersion,
        UUID lineageId,
        int revisionNumber,
        UUID sourceAddOnId,
        AddOnCreationReason creationReason,
        Set<String> allowedPlanCodes,
        Set<String> blockedPlanCodes,
        Set<String> dependencyCodes,
        Set<String> exclusionCodes,
        List<FeatureItem> features
) {
    public record FeatureItem(UUID id, String featureCode, List<QuotaLimitEntry> quotaConfigs) {}
}
