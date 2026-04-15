package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreatePlanRequest(
        @NotBlank String code,
        @NotBlank String name,
        String description,
        @NotNull BigDecimal price,
        @NotNull BillingCycle billingCycle
) {}
