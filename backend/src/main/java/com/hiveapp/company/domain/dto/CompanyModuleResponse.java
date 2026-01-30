package com.hiveapp.company.domain.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class CompanyModuleResponse {
    private UUID id;
    private UUID moduleId;
    private boolean active;
    private Instant activatedAt;
}
