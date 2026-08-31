package com.hiveapp.platform.admin.dto;

import com.hiveapp.platform.admin.domain.constant.AdminRoleStatus;

import java.util.List;
import java.util.UUID;

public record AdminRoleImpactDto(
        UUID roleId,
        long version,
        AdminRoleStatus currentStatus,
        AdminRoleStatus proposedStatus,
        long assignmentCount,
        List<String> permissionsAdded,
        List<String> permissionsRemoved,
        long operatorsLosingLastPermissionSource,
        boolean actorMayLoseAccess,
        boolean confirmationRequired
) {
    public AdminRoleImpactDto {
        permissionsAdded = List.copyOf(permissionsAdded);
        permissionsRemoved = List.copyOf(permissionsRemoved);
    }
}
