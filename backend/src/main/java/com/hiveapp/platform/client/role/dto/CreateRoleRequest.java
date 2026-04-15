package com.hiveapp.platform.client.role.dto;

import java.util.UUID;
import jakarta.validation.constraints.NotBlank;

public record CreateRoleRequest(
    UUID companyId,
    @NotBlank String name,
    String description
) {}
