package com.hiveapp.platform.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;
import java.util.UUID;

public record AdminRolePermissionSetRequest(
        @NotNull List<UUID> permissionIds,
        @PositiveOrZero Long expectedVersion,
        @PositiveOrZero Long confirmedAssignmentCount
) {
    public AdminRolePermissionSetRequest {
        permissionIds = permissionIds == null ? List.of() : List.copyOf(permissionIds);
    }
}
