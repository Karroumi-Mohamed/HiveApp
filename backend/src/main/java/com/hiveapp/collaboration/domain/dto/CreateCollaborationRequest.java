package com.hiveapp.collaboration.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class CreateCollaborationRequest {

    @NotNull(message = "Provider account ID is required")
    private UUID providerAccountId;

    @NotNull(message = "Company ID is required")
    private UUID companyId;

    private List<UUID> permissionIds;
}
