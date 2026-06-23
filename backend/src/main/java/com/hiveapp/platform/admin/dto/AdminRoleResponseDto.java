package com.hiveapp.platform.admin.dto;

import java.util.List;
import java.util.UUID;

public record AdminRoleResponseDto(
        UUID id,
        String name,
        String description,
        boolean isActive,
        List<AdminPermissionSummaryDto> permissions
) {
}
