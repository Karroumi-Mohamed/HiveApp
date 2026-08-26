package com.hiveapp.platform.client.plan.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record ProductPriceReplacementPreviewRequest(
        @NotNull UUID currentPriceId,
        @PositiveOrZero long currentVersion,
        @PositiveOrZero long successorVersion
) {}
