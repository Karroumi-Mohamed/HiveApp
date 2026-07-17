package com.hiveapp.platform.client.role.dto;

import jakarta.validation.constraints.PositiveOrZero;

public record RoleImpactConfirmationRequest(
        @PositiveOrZero Long expectedVersion,
        @PositiveOrZero Long confirmedAssignmentCount
) {}
