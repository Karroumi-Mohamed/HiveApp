package com.hiveapp.platform.client.member.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record InitialRoleAssignmentRequest(
        @NotNull UUID roleId,
        UUID companyId
) {
}
