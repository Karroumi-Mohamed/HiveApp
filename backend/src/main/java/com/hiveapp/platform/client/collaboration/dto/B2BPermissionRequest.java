package com.hiveapp.platform.client.collaboration.dto;

import java.util.UUID;
import jakarta.validation.constraints.NotBlank;

public record B2BPermissionRequest(
    @NotBlank String permissionCode
) {}
