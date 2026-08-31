package com.hiveapp.platform.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Operator justification for cancelling an outstanding Account subscription change. */
public record AdminSubscriptionChangeCancelRequest(
        @NotBlank @Size(max = 2000) String reason
) {}
