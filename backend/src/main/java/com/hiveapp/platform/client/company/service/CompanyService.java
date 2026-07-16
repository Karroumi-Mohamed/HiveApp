package com.hiveapp.platform.client.company.service;

import java.util.List;
import java.util.UUID;
import com.hiveapp.platform.client.account.domain.entity.Company;

public interface CompanyService {
    CompanyMutationResult createCompany(UUID accountId, String name, String legalName, String taxId, String industry, String country, String address, String logoUrl);
    List<Company> getAccountCompanies(UUID accountId);
    Company getCompany(UUID accountId, UUID id);
    CompanyMutationResult updateCompany(UUID accountId, UUID id, String name, String legalName, String taxId, String industry, String country, String address, String logoUrl);
    void deactivateCompany(UUID accountId, UUID id);
    Company reactivateCompany(UUID accountId, UUID id);
}
