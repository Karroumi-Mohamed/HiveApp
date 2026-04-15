package com.hiveapp.platform.client.company.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCompanyRequest(
    @NotBlank String name,
    String legalName,
    String taxId,
    String industry,
    String country,
    String address
) {}
