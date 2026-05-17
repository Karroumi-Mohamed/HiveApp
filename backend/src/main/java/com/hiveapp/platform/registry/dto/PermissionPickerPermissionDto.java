package com.hiveapp.platform.registry.dto;

public record PermissionPickerPermissionDto(
        String code,
        String name,
        String description,
        String action,
        String resource
) {
}
