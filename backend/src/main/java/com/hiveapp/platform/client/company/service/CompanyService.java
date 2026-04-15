package com.hiveapp.platform.client.company.service;

import java.util.List;
import java.util.UUID;
import com.hiveapp.platform.client.account.domain.entity.Company;

public interface CompanyService {
    Company createCompany(UUID accountId, String name, String legalName, String taxId, String industry, String country, String address);
    List<Company> getAccountCompanies(UUID accountId);
    Company getCompany(UUID id);
    Company updateCompany(UUID id, String name, String legalName, String industry, String country);
    void deactivateCompany(UUID id);
}
