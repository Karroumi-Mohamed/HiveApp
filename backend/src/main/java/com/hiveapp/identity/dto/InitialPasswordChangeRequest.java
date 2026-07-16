package com.hiveapp.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InitialPasswordChangeRequest(
        @NotBlank @Size(min = 8) String newPassword
) {
}
