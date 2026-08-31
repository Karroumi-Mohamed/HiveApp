package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.AddOnCreationReason;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import com.hiveapp.shared.money.ExactDecimal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record AddOnDto(
        UUID id,
        String code,
        String name,
        String description,
        @ExactDecimal BigDecimal price,
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
        List<FeatureItem> features,
        ProductSalesVisibility salesVisibility,
        long version
) {
    public AddOnDto(
            UUID id, String code, String name, String description, BigDecimal price,
            String currencyCode, BillingCycle billingCycle, AddOnStatus status,
            long definitionVersion, UUID lineageId, int revisionNumber, UUID sourceAddOnId,
            AddOnCreationReason creationReason, Set<String> allowedPlanCodes,
            Set<String> blockedPlanCodes, Set<String> dependencyCodes, Set<String> exclusionCodes,
            List<FeatureItem> features
    ) {
        this(id, code, name, description, price, currencyCode, billingCycle, status,
                definitionVersion, lineageId, revisionNumber, sourceAddOnId, creationReason,
                allowedPlanCodes, blockedPlanCodes, dependencyCodes, exclusionCodes, features,
                ProductSalesVisibility.PUBLIC, 0L);
    }

    public record FeatureItem(UUID id, String featureCode, List<QuotaLimitEntry> quotaConfigs) {}
}
