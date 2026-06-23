package com.hiveapp.platform.admin.dto;

import java.util.UUID;

public record AdminRoleSummaryDto(
        UUID id,
        String name,
        String description,
        boolean isActive
) {
}
