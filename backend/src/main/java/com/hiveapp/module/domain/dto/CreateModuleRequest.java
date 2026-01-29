package com.hiveapp.module.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CreateModuleRequest {

    @NotBlank(message = "Module code is required")
    @Size(max = 50)
    private String code;

    @NotBlank(message = "Module name is required")
    @Size(max = 100)
    private String name;

    private String description;
    private String icon;
    private Integer sortOrder;
}
