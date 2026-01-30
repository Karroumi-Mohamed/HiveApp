package com.hiveapp.company.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class CreateCompanyRequest {

    @NotNull(message = "Account ID is required")
    private UUID accountId;

    @NotBlank(message = "Company name is required")
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
}
