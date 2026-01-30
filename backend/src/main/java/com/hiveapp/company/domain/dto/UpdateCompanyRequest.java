package com.hiveapp.company.domain.dto;

import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UpdateCompanyRequest {

    @Size(max = 100)
    private String name;

    private String legalName;

    @Size(max = 50)
    private String taxId;

    @Size(max = 100)
    private String industry;

    @Size(max = 100)
    private String country;

    private String address;

    private String logoUrl;
}
