package com.hiveapp.platform.registry.dto.picker;

public record PermissionPickerPermissionDto(
        String code,
        String name,
        String description,
        String action,
        String resource
) {
}
