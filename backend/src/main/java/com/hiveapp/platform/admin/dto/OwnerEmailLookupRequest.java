package com.hiveapp.platform.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Keeps sensitive identity-search input out of URLs and access logs. */
public record OwnerEmailLookupRequest(
        @NotBlank
        @Email
        @Size(max = 320)
        String ownerEmail
) {
}
