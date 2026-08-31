package com.hiveapp.platform.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateAdminRoleRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        @Size(max = 500) List<UUID> permissionIds
) {
    public CreateAdminRoleRequest(String name, String description) {
        this(name, description, List.of());
    }

    public CreateAdminRoleRequest {
        permissionIds = permissionIds == null ? List.of() : List.copyOf(permissionIds);
    }
}
