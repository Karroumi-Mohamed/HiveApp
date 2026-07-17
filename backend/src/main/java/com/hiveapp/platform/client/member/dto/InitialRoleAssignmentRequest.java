package com.hiveapp.platform.client.member.dto;

import jakarta.validation.constraints.NotNull;
import com.hiveapp.platform.client.member.domain.constant.RoleAssignmentScope;

import java.util.UUID;

public record InitialRoleAssignmentRequest(
        @NotNull UUID roleId,
        @NotNull RoleAssignmentScope scope,
        UUID companyId
) {
    public InitialRoleAssignmentRequest(UUID roleId, UUID companyId) {
        this(roleId, companyId == null ? RoleAssignmentScope.ACCOUNT : RoleAssignmentScope.COMPANY, companyId);
    }
}
