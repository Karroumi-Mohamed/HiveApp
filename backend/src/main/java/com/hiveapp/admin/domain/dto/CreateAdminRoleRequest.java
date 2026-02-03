package com.hiveapp.admin.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class CreateAdminRoleRequest {

    @NotBlank(message = "Role name is required")
    @Size(max = 100)
    private String name;

    private String description;

    private List<UUID> permissionIds;
}
