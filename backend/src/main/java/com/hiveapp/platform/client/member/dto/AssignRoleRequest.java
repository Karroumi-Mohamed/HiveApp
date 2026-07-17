package com.hiveapp.platform.client.member.dto;

import java.util.UUID;
import com.hiveapp.platform.client.member.domain.constant.RoleAssignmentScope;
import jakarta.validation.constraints.NotNull;

public record AssignRoleRequest(
    @NotNull UUID roleId,
    @NotNull RoleAssignmentScope scope,
    UUID companyId
) {
    public AssignRoleRequest(UUID roleId, UUID companyId) {
        this(roleId, companyId == null ? RoleAssignmentScope.ACCOUNT : RoleAssignmentScope.COMPANY, companyId);
    }
}
