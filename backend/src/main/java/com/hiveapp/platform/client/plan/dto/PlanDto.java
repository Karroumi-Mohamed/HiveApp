package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;

import java.math.BigDecimal;
import java.util.UUID;

public record PlanDto(
        UUID id,
        String code,
        String name,
        String description,
        BigDecimal price,
        BillingCycle billingCycle,
        boolean isActive
) {}
