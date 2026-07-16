package com.hiveapp.platform.client.company.dto;

import com.hiveapp.platform.client.company.domain.constant.GroupTemplateScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateGroupTemplateRequest(
        @NotNull UUID companyId,
        @NotNull UUID sourceGroupId,
        @NotNull GroupTemplateScope scope,
        @NotBlank @Size(max = 160) String name
) {}
