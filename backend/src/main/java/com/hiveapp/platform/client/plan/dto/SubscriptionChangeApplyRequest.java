package com.hiveapp.platform.client.plan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Applies exactly the subscription selection represented by a recent signed preview. */
public record SubscriptionChangeApplyRequest(
        @NotNull @Valid SubscriptionChangeRequest selection,
        @NotBlank @Size(max = 2048) String previewToken
) {}
