package com.hiveapp.platform.client.company.dto;

public record UpdateCompanyRequest(
    String name,
    String legalName,
    String industry,
    String country
) {}
