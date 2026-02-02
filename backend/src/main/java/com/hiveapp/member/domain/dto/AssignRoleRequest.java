package com.hiveapp.member.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class AssignRoleRequest {

    @NotNull(message = "Role ID is required")
    private UUID roleId;

    private UUID companyId;
}
