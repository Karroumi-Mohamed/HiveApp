package com.hiveapp.platform.client.plan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ProductPriceChangeConfirmation(
        @Valid @NotNull ProductPriceChangeRequest change,
        @NotBlank String previewToken,
        @NotNull UUID idempotencyKey) {}
