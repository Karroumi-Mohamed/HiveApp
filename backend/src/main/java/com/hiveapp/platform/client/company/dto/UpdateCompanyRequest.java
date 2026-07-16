package com.hiveapp.platform.client.company.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateCompanyRequest(
    @Size(max = 160) String name,
    @Size(max = 240) String legalName,
    @Size(max = 100) String taxId,
    @Size(max = 120) String industry,
    @Pattern(regexp = "(?i)[a-z]{2}", message = "must be a two-letter country code") String country,
    @Size(max = 1000) String address,
    @Size(max = 2048) String logoUrl
) {
    public UpdateCompanyRequest(String name, String legalName, String industry, String country) {
        this(name, legalName, null, industry, country, null, null);
    }
}
