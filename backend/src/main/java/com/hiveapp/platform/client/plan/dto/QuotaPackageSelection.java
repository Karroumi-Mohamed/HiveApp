package com.hiveapp.platform.client.plan.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record QuotaPackageSelection(
        @NotBlank String packageCode,
        @Min(1) int quantity
) {}
