package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageCreationReason;
import com.hiveapp.shared.money.ExactDecimal;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public record QuotaPackageDto(
        UUID id,
        String code,
        String name,
        String description,
        String featureCode,
        String resource,
        long capacityPerUnit,
        @ExactDecimal BigDecimal price,
        String currencyCode,
        BillingCycle billingCycle,
        boolean repeatable,
        int maximumQuantity,
        QuotaPackageStatus status,
        long definitionVersion,
        Set<String> allowedPlanCodes,
        Set<String> allowedAddOnCodes,
        ProductSalesVisibility salesVisibility,
        long version,
        UUID lineageId,
        int revisionNumber,
        UUID sourceQuotaPackageId,
        QuotaPackageCreationReason creationReason
) {
    public QuotaPackageDto(
            UUID id, String code, String name, String description, String featureCode,
            String resource, long capacityPerUnit, BigDecimal price, String currencyCode,
            BillingCycle billingCycle, boolean repeatable, int maximumQuantity,
            QuotaPackageStatus status, long definitionVersion, Set<String> allowedPlanCodes,
            Set<String> allowedAddOnCodes, ProductSalesVisibility salesVisibility, long version
    ) {
        this(id, code, name, description, featureCode, resource, capacityPerUnit, price,
                currencyCode, billingCycle, repeatable, maximumQuantity, status,
                definitionVersion, allowedPlanCodes, allowedAddOnCodes, salesVisibility, version,
                id, 1, null, QuotaPackageCreationReason.CREATED);
    }

    public QuotaPackageDto(
            UUID id, String code, String name, String description, String featureCode,
            String resource, long capacityPerUnit, BigDecimal price, String currencyCode,
            BillingCycle billingCycle, boolean repeatable, int maximumQuantity,
            QuotaPackageStatus status, long definitionVersion, Set<String> allowedPlanCodes,
            Set<String> allowedAddOnCodes
    ) {
        this(id, code, name, description, featureCode, resource, capacityPerUnit, price,
                currencyCode, billingCycle, repeatable, maximumQuantity, status,
                definitionVersion, allowedPlanCodes, allowedAddOnCodes,
                ProductSalesVisibility.PUBLIC, 0L, id, 1, null,
                QuotaPackageCreationReason.CREATED);
    }
}
