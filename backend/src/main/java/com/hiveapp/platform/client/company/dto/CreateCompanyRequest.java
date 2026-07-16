package com.hiveapp.platform.client.company.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCompanyRequest(
    @NotBlank @Size(max = 160) String name,
    @Size(max = 240) String legalName,
    @Size(max = 100) String taxId,
    @Size(max = 120) String industry,
    @NotBlank @Pattern(regexp = "(?i)[a-z]{2}", message = "must be a two-letter country code") String country,
    @Size(max = 1000) String address,
    @Size(max = 2048) String logoUrl
) {
    public CreateCompanyRequest(
            String name,
            String legalName,
            String taxId,
            String industry,
            String country,
            String address) {
        this(name, legalName, taxId, industry, country, address, null);
    }
}
