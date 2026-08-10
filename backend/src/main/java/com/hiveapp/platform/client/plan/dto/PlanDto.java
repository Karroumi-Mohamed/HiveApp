package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.PlanCreationReason;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record PlanDto(
        UUID id,
        String code,
        String name,
        String description,
        BigDecimal price,
        String currencyCode,
        BillingCycle billingCycle,
        PlanStatus status,
        UUID lineageId,
        int revisionNumber,
        UUID sourcePlanId,
        PlanCreationReason creationReason
) {}
