package com.hiveapp.platform.registry.dto.publicapi;

import java.util.List;

public record PublicFeatureCatalogModuleDto(
        String code,
        List<PublicFeatureCatalogFeatureDto> features
) {
}
