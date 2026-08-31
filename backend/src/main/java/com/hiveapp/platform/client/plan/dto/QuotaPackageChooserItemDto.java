package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.CommercialChoiceState;
import com.hiveapp.platform.client.plan.domain.constant.CommercialTargetingMode;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;

import java.util.UUID;

public record QuotaPackageChooserItemDto(
        UUID id,
        String code,
        String name,
        String featureCode,
        String resource,
        QuotaPackageStatus status,
        UUID lineageId,
        int revisionNumber,
        ProductSalesVisibility salesVisibility,
        CommercialTargetingMode targetingMode,
        CommercialChoiceState choiceState
) {}
