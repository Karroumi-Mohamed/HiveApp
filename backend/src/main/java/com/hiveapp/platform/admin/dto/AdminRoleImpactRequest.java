package com.hiveapp.platform.admin.dto;

import com.hiveapp.platform.admin.domain.constant.AdminRoleStatus;

import java.util.List;
import java.util.UUID;

public record AdminRoleImpactRequest(
        List<UUID> permissionIds,
        AdminRoleStatus status
) {
    public AdminRoleImpactRequest {
        permissionIds = permissionIds == null ? null : List.copyOf(permissionIds);
    }
}
