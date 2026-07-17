package com.hiveapp.platform.client.role.dto;

import java.util.UUID;
import com.hiveapp.platform.client.role.domain.constant.RoleTemplateBoundary;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateRoleRequest(
    @NotNull RoleTemplateBoundary templateBoundary,
    UUID boundaryCompanyId,
    @NotBlank String name,
    String description
) {
    public CreateRoleRequest(UUID companyId, String name, String description) {
        this(companyId == null ? RoleTemplateBoundary.ACCOUNT : RoleTemplateBoundary.COMPANY,
                companyId, name, description);
    }
}
