package com.hiveapp.platform.admin.dto;

import java.util.List;
import java.util.UUID;

public record AdminRoleResponseDto(
        UUID id,
        String name,
        String description,
        boolean isActive,
        /** Operators currently holding this role — what a deactivation would affect. */
        long assignedOperatorCount,
        List<AdminPermissionSummaryDto> permissions
) {
}
