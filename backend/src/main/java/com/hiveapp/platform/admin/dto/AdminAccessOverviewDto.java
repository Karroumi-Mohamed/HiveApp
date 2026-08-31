package com.hiveapp.platform.admin.dto;

public record AdminAccessOverviewDto(
        long totalOperators,
        long activeOperators,
        long inactiveOperators,
        long superAdmins,
        long totalRoles,
        long activeRoles,
        long inactiveRoles
) {
}
