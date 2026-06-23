package com.hiveapp.platform.admin.dto;

import java.util.UUID;

public record AdminPermissionSummaryDto(
        UUID id,
        String code,
        String name,
        String description,
        String action,
        String resource
) {
}
