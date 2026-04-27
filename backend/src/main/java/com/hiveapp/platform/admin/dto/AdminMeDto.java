package com.hiveapp.platform.admin.dto;

import java.util.Set;
import java.util.UUID;

public record AdminMeDto(
    UUID id,
    String email,
    boolean isSuperAdmin,
    boolean isActive,
    Set<String> permissions
) {}
