package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Set;

public record CreateAddOnRequest(
        @NotBlank String code,
        @NotBlank String name,
        String description,
        @NotNull BigDecimal price,
        @NotBlank String currencyCode,
        @NotNull BillingCycle billingCycle,
        Set<String> allowedPlanCodes,
        Set<String> blockedPlanCodes,
        Set<String> dependencyCodes,
        Set<String> exclusionCodes
) {}
