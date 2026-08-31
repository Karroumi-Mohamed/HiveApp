package com.hiveapp.platform.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ChangeOperatorEmailRequest(
        @NotBlank @Email String email
) {
}
