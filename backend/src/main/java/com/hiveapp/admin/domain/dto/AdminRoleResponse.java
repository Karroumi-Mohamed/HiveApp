package com.hiveapp.admin.domain.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class AdminRoleResponse {
    private UUID id;
    private String name;
    private String description;
    private boolean active;
    private List<AdminPermissionResponse> permissions;
}
