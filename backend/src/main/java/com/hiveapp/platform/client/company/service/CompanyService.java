package com.hiveapp.platform.client.company.service;

import com.hiveapp.platform.client.company.dto.CompanyDto;

import java.util.List;
import java.util.UUID;

/**
 * Company application contract. Methods return read models rather than persistence entities so
 * response composition, and the transaction it depends on, stay inside the service.
 */
public interface CompanyService {
    CompanyDto createCompany(UUID accountId, String name, String legalName, String taxId, String industry, String country, String address, String logoUrl);
    List<CompanyDto> getAccountCompanies(UUID accountId);
    CompanyDto getCompany(UUID accountId, UUID id);
    CompanyDto updateCompany(UUID accountId, UUID id, String name, String legalName, String taxId, String industry, String country, String address, String logoUrl);
    void deactivateCompany(UUID accountId, UUID id);
    CompanyDto reactivateCompany(UUID accountId, UUID id);
}
