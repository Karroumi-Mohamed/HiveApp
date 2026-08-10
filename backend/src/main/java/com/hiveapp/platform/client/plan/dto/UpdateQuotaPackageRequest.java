package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Set;

public record UpdateQuotaPackageRequest(
        @NotBlank String name,
        String description,
        @NotBlank String featureCode,
        @NotBlank String resource,
        @Min(1) long capacityPerUnit,
        @NotNull BigDecimal price,
        @NotBlank String currencyCode,
        @NotNull BillingCycle billingCycle,
        boolean repeatable,
        @Min(1) int maximumQuantity,
        Set<String> allowedPlanCodes,
        Set<String> allowedAddOnCodes
) {}
