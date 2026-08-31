package com.hiveapp.platform.client.collaboration.dto;

import java.util.Set;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InitiateCollaborationRequest(
        @NotBlank String shareCode,
        @NotBlank @Size(max = 1000) String purpose,
        Set<@NotBlank String> requestedPermissionCodes
) {}
