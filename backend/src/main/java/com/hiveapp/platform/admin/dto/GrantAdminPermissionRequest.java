package com.hiveapp.platform.admin.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record GrantAdminPermissionRequest(
        @NotNull UUID adminPermissionId
) {}
