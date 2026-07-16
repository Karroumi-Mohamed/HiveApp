package com.hiveapp.platform.client.company.dto;

import java.util.UUID;
import java.util.List;

public record CompanyDto(
    UUID id, 
    String name, 
    String legalName, 
    String taxId,
    String industry,
    String country,
    String address,
    String logoUrl,
    boolean isActive,
    List<String> warnings
) {}
