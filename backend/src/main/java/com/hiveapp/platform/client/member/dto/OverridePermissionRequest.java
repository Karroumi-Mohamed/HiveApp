package com.hiveapp.platform.client.member.dto;

import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.hiveapp.platform.client.member.domain.constant.PermissionOverrideDecision;
import com.hiveapp.platform.client.member.domain.constant.PermissionOverrideScope;
import java.time.Instant;

public record OverridePermissionRequest(
    @NotBlank String permissionCode,
    @NotNull PermissionOverrideScope scope,
    UUID companyId,
    @NotNull PermissionOverrideDecision decision,
    @NotBlank @Size(max = 500) String reason,
    Instant expiresAt
) {}
