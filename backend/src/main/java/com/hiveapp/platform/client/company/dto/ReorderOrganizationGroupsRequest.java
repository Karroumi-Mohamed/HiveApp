package com.hiveapp.platform.client.company.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ReorderOrganizationGroupsRequest(
        @NotNull UUID companyId,
        UUID parentId,
        @NotEmpty List<@NotNull UUID> orderedGroupIds
) {}
