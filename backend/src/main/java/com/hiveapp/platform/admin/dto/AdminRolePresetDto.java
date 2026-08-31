package com.hiveapp.platform.admin.dto;

import java.util.List;

public record AdminRolePresetDto(
        String code,
        String name,
        String description,
        List<AdminPermissionSummaryDto> permissions
) {
    public AdminRolePresetDto {
        permissions = List.copyOf(permissions);
    }
}
