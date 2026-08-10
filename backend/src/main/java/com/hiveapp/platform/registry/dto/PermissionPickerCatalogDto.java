package com.hiveapp.platform.registry.dto;

import java.util.List;

public record PermissionPickerCatalogDto(
        String registryVersion,
        PermissionCatalogAudience audience,
        List<PermissionPickerModuleDto> availableChoices,
        List<PermissionPickerSelectionDto> currentSelections
) {
}
