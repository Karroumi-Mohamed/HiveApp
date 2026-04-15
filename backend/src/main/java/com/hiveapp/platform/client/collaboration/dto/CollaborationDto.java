package com.hiveapp.platform.client.collaboration.dto;

import java.util.UUID;
import com.hiveapp.platform.client.collaboration.domain.constant.CollaborationStatus;

public record CollaborationDto(
    UUID id, 
    UUID clientAccountId, 
    UUID providerAccountId, 
    UUID companyId, 
    CollaborationStatus status
) {}
