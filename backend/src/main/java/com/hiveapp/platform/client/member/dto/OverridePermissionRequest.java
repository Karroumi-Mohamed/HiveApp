package com.hiveapp.platform.client.member.dto;

import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OverridePermissionRequest(
    @NotBlank String permissionCode,
    @NotNull UUID companyId,
    boolean decision
) {}
