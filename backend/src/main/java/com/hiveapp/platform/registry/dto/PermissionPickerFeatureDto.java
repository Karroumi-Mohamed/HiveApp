package com.hiveapp.platform.registry.dto;

import java.util.List;

public record PermissionPickerFeatureDto(
        String code,
        String displayName,
        String description,
        List<PermissionPickerPermissionDto> permissions
) {
}
