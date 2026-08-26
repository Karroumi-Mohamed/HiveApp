package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.CommercialChoiceState;
import com.hiveapp.platform.client.plan.domain.constant.CommercialTargetingMode;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;

import java.util.UUID;

public record AddOnChooserItemDto(
        UUID id,
        String code,
        String name,
        AddOnStatus status,
        UUID lineageId,
        int revisionNumber,
        ProductSalesVisibility salesVisibility,
        CommercialTargetingMode targetingMode,
        CommercialChoiceState choiceState
) {}
