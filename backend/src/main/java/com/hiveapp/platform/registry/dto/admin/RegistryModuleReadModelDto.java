package com.hiveapp.platform.registry.dto.admin;

import java.util.List;

public record RegistryModuleReadModelDto(
        String code,
        List<RegistryFeatureReadModelDto> features
) {
}
