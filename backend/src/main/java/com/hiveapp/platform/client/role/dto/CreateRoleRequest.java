package com.hiveapp.platform.client.role.dto;

import java.util.UUID;
import com.hiveapp.platform.client.role.domain.constant.RoleTemplateBoundary;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRoleRequest(
    @NotNull RoleTemplateBoundary templateBoundary,
    UUID boundaryCompanyId,
    @NotBlank @Size(max = 100) String name,
    @Size(max = 500) String description
) {
    public CreateRoleRequest(UUID companyId, String name, String description) {
        this(companyId == null ? RoleTemplateBoundary.ACCOUNT : RoleTemplateBoundary.COMPANY,
                companyId, name, description);
    }
}
