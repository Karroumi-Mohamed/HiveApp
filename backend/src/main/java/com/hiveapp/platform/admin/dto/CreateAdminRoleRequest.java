package com.hiveapp.platform.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAdminRoleRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description
) {}
