package com.hiveapp.platform.registry.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FeatureControlUpdateRequest(
        boolean enabled,
        @NotBlank @Size(max = 500) String reason
) {
}
