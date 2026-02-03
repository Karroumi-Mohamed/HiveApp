package com.hiveapp.collaboration.domain.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class CollaborationResponse {
    private UUID id;
    private UUID clientAccountId;
    private UUID providerAccountId;
    private UUID companyId;
    private String status;
    private Instant requestedAt;
    private Instant acceptedAt;
    private Instant revokedAt;
    private List<UUID> permissionIds;
}
