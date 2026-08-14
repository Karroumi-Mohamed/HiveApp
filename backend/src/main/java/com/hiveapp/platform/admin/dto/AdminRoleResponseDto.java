package com.hiveapp.platform.admin.dto;

import com.hiveapp.platform.admin.domain.constant.AdminRoleStatus;
import com.hiveapp.platform.admin.domain.constant.AdminRoleAction;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record AdminRoleResponseDto(
        UUID id,
        String name,
        String description,
        AdminRoleStatus status,
        boolean isActive,
        long version,
        Instant createdAt,
        Instant updatedAt,
        boolean deletable,
        /** Operators currently holding this role — what a deactivation would affect. */
        long assignedOperatorCount,
        List<AdminPermissionSummaryDto> permissions,
        Set<AdminRoleAction> availableActions
) {
}
