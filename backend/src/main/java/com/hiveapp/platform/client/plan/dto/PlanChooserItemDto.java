package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.CommercialChoiceState;
import com.hiveapp.platform.client.plan.domain.constant.PlanExtensionPolicy;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;

import java.util.UUID;

public record PlanChooserItemDto(
        UUID id,
        String code,
        String name,
        PlanStatus status,
        UUID lineageId,
        int revisionNumber,
        PlanExtensionPolicy extensionPolicy,
        ProductSalesVisibility salesVisibility,
        CommercialChoiceState choiceState
) {}
