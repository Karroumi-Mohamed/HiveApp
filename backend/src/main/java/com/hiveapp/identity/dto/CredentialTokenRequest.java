package com.hiveapp.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record CredentialTokenRequest(@NotBlank String token) {
}
