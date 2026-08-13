package com.hiveapp.platform.admin.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record BulkAssignRoleRequest(
        @NotEmpty @Size(max = 100) List<UUID> ids,
        @NotNull UUID adminRoleId
) {}
