package com.hiveapp.platform.client.member.dto;

import com.hiveapp.platform.client.member.domain.constant.RoleAssignmentScope;
import com.hiveapp.platform.client.role.domain.constant.RoleStatus;

import java.util.UUID;

public record MemberRoleAssignmentDto(
        UUID assignmentId,
        UUID roleId,
        String roleName,
        RoleStatus roleStatus,
        RoleAssignmentScope scope,
        UUID companyId,
        String companyName
) {
}
