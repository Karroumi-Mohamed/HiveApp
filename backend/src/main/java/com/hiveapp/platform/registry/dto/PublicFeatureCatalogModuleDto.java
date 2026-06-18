package com.hiveapp.platform.registry.dto;

import java.util.List;

public record PublicFeatureCatalogModuleDto(
        String code,
        List<PublicFeatureCatalogFeatureDto> features
) {
}
