package com.hiveapp.module.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class CreateFeatureRequest {

    @NotNull(message = "Module ID is required")
    private UUID moduleId;

    @NotBlank(message = "Feature code is required")
    @Size(max = 100)
    private String code;

    @NotBlank(message = "Feature name is required")
    @Size(max = 150)
    private String name;

    private String description;
    private Integer sortOrder;
}
