package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public record QuotaPackageDto(
        UUID id,
        String code,
        String name,
        String description,
        String featureCode,
        String resource,
        long capacityPerUnit,
        BigDecimal price,
        String currencyCode,
        BillingCycle billingCycle,
        boolean repeatable,
        int maximumQuantity,
        QuotaPackageStatus status,
        long definitionVersion,
        Set<String> allowedPlanCodes,
        Set<String> allowedAddOnCodes
) {}
