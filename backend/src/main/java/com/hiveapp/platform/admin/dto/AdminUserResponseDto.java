package com.hiveapp.platform.admin.dto;

import java.util.List;
import java.util.UUID;

public record AdminUserResponseDto(
    UUID id,
    UUID userId,
    String email,
    boolean isSuperAdmin,
    boolean isActive,
    List<AdminRoleSummaryDto> roles
) {}
