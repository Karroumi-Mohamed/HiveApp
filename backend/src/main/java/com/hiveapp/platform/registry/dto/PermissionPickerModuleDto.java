package com.hiveapp.platform.registry.dto;

import java.util.List;

public record PermissionPickerModuleDto(
        String code,
        List<PermissionPickerFeatureDto> features
) {
}
