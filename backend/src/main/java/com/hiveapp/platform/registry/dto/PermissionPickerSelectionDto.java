package com.hiveapp.platform.registry.dto;

public record PermissionPickerSelectionDto(
        String permissionCode,
        boolean available,
        PermissionUnavailableReason unavailableReason,
        String explanation
) {
}
