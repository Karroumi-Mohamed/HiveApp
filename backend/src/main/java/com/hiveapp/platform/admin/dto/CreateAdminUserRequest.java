package com.hiveapp.platform.admin.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateAdminUserRequest(
        @NotNull UUID userId,
        boolean isSuperAdmin
) {}
