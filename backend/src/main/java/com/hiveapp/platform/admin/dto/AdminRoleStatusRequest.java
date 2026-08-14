package com.hiveapp.platform.admin.dto;

import com.hiveapp.platform.admin.domain.constant.AdminRoleStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record AdminRoleStatusRequest(
        @NotNull AdminRoleStatus status,
        @PositiveOrZero Long expectedVersion,
        @PositiveOrZero Long confirmedAssignmentCount
) {}
