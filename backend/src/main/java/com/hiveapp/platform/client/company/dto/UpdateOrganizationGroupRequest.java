package com.hiveapp.platform.client.company.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record UpdateOrganizationGroupRequest(
        @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") @Size(max = 160) String name,
        @Size(max = 1000) String description,
        List<@Size(max = 160) String> positionSuggestions
) {}
