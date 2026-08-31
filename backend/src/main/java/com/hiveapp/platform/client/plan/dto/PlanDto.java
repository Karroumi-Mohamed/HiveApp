package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.PlanCreationReason;
import com.hiveapp.platform.client.plan.domain.constant.PlanExtensionPolicy;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;
import com.hiveapp.shared.money.ExactDecimal;

import java.math.BigDecimal;
import java.util.UUID;

public record PlanDto(
        UUID id,
        String code,
        String name,
        String description,
        @ExactDecimal BigDecimal price,
        String currencyCode,
        BillingCycle billingCycle,
        PlanStatus status,
        UUID lineageId,
        int revisionNumber,
        UUID sourcePlanId,
        PlanCreationReason creationReason,
        PlanExtensionPolicy extensionPolicy,
        ProductSalesVisibility salesVisibility,
        long version
) {
    public PlanDto(
            UUID id, String code, String name, String description, BigDecimal price,
            String currencyCode, BillingCycle billingCycle, PlanStatus status,
            UUID lineageId, int revisionNumber, UUID sourcePlanId,
            PlanCreationReason creationReason
    ) {
        this(id, code, name, description, price, currencyCode, billingCycle, status,
                lineageId, revisionNumber, sourcePlanId, creationReason,
                PlanExtensionPolicy.OPEN_COMPATIBLE, ProductSalesVisibility.PUBLIC, 0L);
    }
}
