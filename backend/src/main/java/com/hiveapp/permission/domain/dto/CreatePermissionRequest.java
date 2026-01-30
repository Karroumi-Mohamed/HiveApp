package com.hiveapp.permission.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class CreatePermissionRequest {

    @NotNull(message = "Feature ID is required")
    private UUID featureId;

    @NotBlank(message = "Permission code is required")
    @Size(max = 150)
    private String code;

    @NotBlank(message = "Permission name is required")
    @Size(max = 200)
    private String name;

    private String description;

    @NotBlank(message = "Action is required")
    @Size(max = 50)
    private String action;

    @NotBlank(message = "Resource is required")
    @Size(max = 100)
    private String resource;
}
