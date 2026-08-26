package com.hiveapp.platform.client.plan.dto;

import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ProductVisibilityMutationRequest(
        @PositiveOrZero long expectedVersion,
        @NotNull ProductSalesVisibility salesVisibility,
        @NotBlank @Size(max = 500) String reason,
        @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String previewToken
) {}
