package com.hiveapp.platform.registry.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmergencyRuntimeUpdateRequest(
        boolean enabled,
        @NotBlank @Size(max = 500) String reason,
        @AssertTrue boolean impactConfirmed,
        @AssertTrue boolean communicationConfirmed
) {
}
