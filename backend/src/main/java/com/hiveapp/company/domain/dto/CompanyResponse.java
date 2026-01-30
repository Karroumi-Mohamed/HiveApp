package com.hiveapp.company.domain.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class CompanyResponse {
    private UUID id;
    private UUID accountId;
    private String name;
    private String legalName;
    private String taxId;
    private String industry;
    private String country;
    private String address;
    private String logoUrl;
    private boolean active;
    private Instant createdAt;
    private List<CompanyModuleResponse> activeModules;
}
