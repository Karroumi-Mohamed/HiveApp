package com.hiveapp.platform.client.collaboration.dto;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;

public record InitiateCollaborationRequest(
    @NotNull UUID companyId
) {}
