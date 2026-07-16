package com.hiveapp.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordCompletionRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8) String newPassword
) {
}
