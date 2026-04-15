package com.hiveapp.platform.client.member.dto;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;

public record AssignRoleRequest(
    @NotNull UUID roleId,
    UUID companyId
) {}
