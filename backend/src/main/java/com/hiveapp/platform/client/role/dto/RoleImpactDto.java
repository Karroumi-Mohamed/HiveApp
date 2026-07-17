package com.hiveapp.platform.client.role.dto;

import com.hiveapp.platform.client.role.domain.constant.RoleChangeType;
import com.hiveapp.platform.client.role.domain.constant.RoleStatus;

import java.util.List;
import java.util.UUID;

public record RoleImpactDto(
        UUID roleId,
        long version,
        RoleStatus status,
        RoleChangeType changeType,
        String permissionCode,
        long assignmentCount,
        long affectedMemberCount,
        long activeMemberCount,
        List<RoleImpactScopeDto> scopes,
        List<String> currentPermissionCodes,
        List<String> permissionsGranted,
        List<String> permissionsLost,
        boolean confirmationRequired
) {}
