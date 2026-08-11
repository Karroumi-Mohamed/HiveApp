package com.hiveapp.platform.registry.dto.picker;

import java.util.List;

public record PermissionPickerModuleDto(
        String code,
        List<PermissionPickerFeatureDto> features
) {
}
