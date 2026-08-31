package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageComparisonField;

import java.util.List;

public record QuotaPackageComparisonDto(
        QuotaPackageDto base,
        QuotaPackageDto candidate,
        boolean directSuccessor,
        List<QuotaPackageComparisonField> changedFields,
        List<QuotaPackagePriceDraftDto> basePrices,
        List<QuotaPackagePriceDraftDto> candidatePrices
) {
    public QuotaPackageComparisonDto {
        changedFields = List.copyOf(changedFields);
        basePrices = List.copyOf(basePrices);
        candidatePrices = List.copyOf(candidatePrices);
    }
}
