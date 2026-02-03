package com.hiveapp.admin.domain.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class AdminUserResponse {
    private UUID id;
    private UUID userId;
    private boolean superAdmin;
    private boolean active;
    private List<AdminRoleResponse> roles;
}
