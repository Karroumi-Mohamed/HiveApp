package com.hiveapp.platform.client.company.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateOrganizationGroupRequest(
        @Size(max = 160) String name,
        @Size(max = 1000) String description,
        List<@Size(max = 160) String> positionSuggestions
) {}
