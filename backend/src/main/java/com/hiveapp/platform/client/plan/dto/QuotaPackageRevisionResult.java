package com.hiveapp.platform.client.plan.dto;

import java.util.List;

public record QuotaPackageRevisionResult(
        QuotaPackageDto successor,
        List<QuotaPackagePriceDraftDto> copiedPriceDrafts,
        List<String> warnings
) {
    public QuotaPackageRevisionResult {
        copiedPriceDrafts = List.copyOf(copiedPriceDrafts);
        warnings = List.copyOf(warnings);
    }
}
