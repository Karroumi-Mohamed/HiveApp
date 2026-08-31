package com.hiveapp.platform.client.role.dto;

import java.util.List;
import java.util.UUID;
import com.hiveapp.platform.client.role.domain.constant.RoleStatus;
import com.hiveapp.platform.client.role.domain.constant.RoleTemplateBoundary;

public record RoleDto(
    UUID id,
    UUID accountId,
    RoleTemplateBoundary templateBoundary,
    UUID boundaryCompanyId,
    String name,
    String description,
    RoleStatus status,
    boolean isSystemRole,
    boolean everAssigned,
    long definitionRevision,
    long version,
    List<String> permissionCodes
) {}
