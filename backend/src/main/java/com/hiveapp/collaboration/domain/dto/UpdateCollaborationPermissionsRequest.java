package com.hiveapp.collaboration.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class UpdateCollaborationPermissionsRequest {

    @NotNull(message = "Permission IDs are required")
    private List<UUID> permissionIds;
}
