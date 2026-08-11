package com.hiveapp.platform.registry.dto.picker;

import com.hiveapp.platform.registry.dto.admin.PermissionCatalogAudience;

import java.util.List;

public record PermissionPickerCatalogDto(
        String registryVersion,
        PermissionCatalogAudience audience,
        List<PermissionPickerModuleDto> availableChoices,
        List<PermissionPickerSelectionDto> currentSelections
) {
}
