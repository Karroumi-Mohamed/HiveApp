package com.hiveapp.platform.registry.dto.picker;

public record PermissionPickerSelectionDto(
        String permissionCode,
        boolean available,
        PermissionUnavailableReason unavailableReason,
        String explanation
) {
}
