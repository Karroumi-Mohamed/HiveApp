package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.CommercialProductType;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ExtensionCompatibilityDto(
        CommercialProductType productType,
        UUID productId,
        String code,
        String name,
        ProductSalesVisibility salesVisibility,
        boolean operatorSelectable,
        boolean clientCatalogVisible,
        List<ExtensionAvailabilityIssue> issues,
        int applicablePriceCount,
        boolean directlySelectable,
        Set<String> requiredAddOnCodes,
        Set<String> featureCodes,
        String quotaFeatureCode,
        String quotaResource,
        Long capacityPerUnit,
        Boolean repeatable,
        Integer maximumQuantity
) {
    public ExtensionCompatibilityDto {
        issues = List.copyOf(issues == null ? List.of() : issues);
        requiredAddOnCodes = Set.copyOf(requiredAddOnCodes == null ? Set.of() : requiredAddOnCodes);
        featureCodes = Set.copyOf(featureCodes == null ? Set.of() : featureCodes);
    }
}
