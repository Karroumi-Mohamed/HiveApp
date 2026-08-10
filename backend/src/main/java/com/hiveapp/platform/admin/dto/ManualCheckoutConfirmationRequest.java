package com.hiveapp.platform.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ManualCheckoutConfirmationRequest(
        @NotBlank @Size(max = 255) String reference,
        @NotBlank @Size(max = 2000) String reason
) {}
