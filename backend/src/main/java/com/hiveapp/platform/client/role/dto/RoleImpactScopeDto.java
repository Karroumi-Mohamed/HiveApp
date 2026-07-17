package com.hiveapp.platform.client.role.dto;

import java.util.UUID;

public record RoleImpactScopeDto(
        String scope,
        UUID companyId,
        long assignmentCount
) {}
