package com.hiveapp.platform.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateAdminRoleFromPresetRequest(
        @NotBlank String presetCode,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        @Size(max = 500) List<UUID> permissionIds
) {}
