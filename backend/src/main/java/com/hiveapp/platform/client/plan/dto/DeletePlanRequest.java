package com.hiveapp.platform.client.plan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record DeletePlanRequest(
        @NotBlank String confirmationName,
        @PositiveOrZero long expectedVersion,
        @NotBlank String previewToken
) {}
