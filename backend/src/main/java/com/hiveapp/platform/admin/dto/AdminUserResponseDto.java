package com.hiveapp.platform.admin.dto;

import java.util.UUID;

public record AdminUserResponseDto(
    UUID id,
    UUID userId,
    String email,
    boolean isSuperAdmin,
    boolean isActive
) {}
