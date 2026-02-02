package com.hiveapp.role.domain.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class RoleResponse {
    private UUID id;
    private UUID accountId;
    private String name;
    private String description;
    private boolean systemRole;
    private boolean active;
    private Instant createdAt;
    private List<UUID> permissionIds;
}
