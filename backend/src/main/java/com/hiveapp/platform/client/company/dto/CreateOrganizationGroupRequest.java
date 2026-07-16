package com.hiveapp.platform.client.company.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateOrganizationGroupRequest(
        @NotNull UUID companyId,
        UUID parentId,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 1000) String description,
        List<@NotBlank @Size(max = 160) String> positionSuggestions
) {}
