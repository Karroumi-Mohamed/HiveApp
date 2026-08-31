package com.hiveapp.platform.admin.dto;

import com.hiveapp.platform.admin.domain.constant.AdminRoleStatus;
import java.util.UUID;

public record AdminRoleSummaryDto(
        UUID id,
        String name,
        String description,
        AdminRoleStatus status,
        boolean isActive
) {
    public AdminRoleSummaryDto(UUID id, String name, String description, boolean isActive) {
        this(id, name, description, isActive ? AdminRoleStatus.ACTIVE : AdminRoleStatus.INACTIVE, isActive);
    }
}
