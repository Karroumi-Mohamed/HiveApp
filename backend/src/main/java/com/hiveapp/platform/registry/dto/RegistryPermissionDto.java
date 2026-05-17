package com.hiveapp.platform.registry.dto;

import java.util.UUID;

public record RegistryPermissionDto(
        UUID id,
        String code,
        String name,
        String description,
        String action,
        String resource
) {
}
