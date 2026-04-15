package com.hiveapp.platform.client.company.dto;

import java.util.UUID;

public record CompanyDto(
    UUID id, 
    String name, 
    String legalName, 
    String industry,
    String country, 
    boolean isActive
) {}
