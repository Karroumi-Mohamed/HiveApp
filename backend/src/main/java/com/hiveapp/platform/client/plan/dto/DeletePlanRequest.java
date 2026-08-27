package com.hiveapp.platform.client.plan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record DeletePlanRequest(
        @NotBlank String confirmationName,
        @PositiveOrZero long expectedVersion,
        @NotBlank @Size(max = 2048) String previewToken
) {}
