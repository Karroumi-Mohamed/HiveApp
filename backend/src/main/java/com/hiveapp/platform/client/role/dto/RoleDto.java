package com.hiveapp.platform.client.role.dto;

import java.util.List;
import java.util.UUID;

public record RoleDto(
    UUID id, 
    String name, 
    String description,
    boolean isSystemRole, 
    List<String> permissionCodes
) {}
