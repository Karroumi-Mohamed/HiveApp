package com.hiveapp.platform.client.company.service.impl;

import com.hiveapp.platform.client.account.domain.entity.Company;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.domain.repository.CompanyRepository;
import com.hiveapp.platform.client.company.service.CompanyService;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyServiceImpl implements CompanyService {

    private final CompanyRepository companyRepository;
    private final AccountRepository accountRepository;

    @Override
    @Transactional
    public Company createCompany(UUID accountId, String name, String legalName, String taxId, String industry, String country, String address) {
        var account = accountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
            
        Company comp = new Company();
        comp.setAccount(account);
        comp.setName(name);
        comp.setLegalName(legalName);
        comp.setTaxId(taxId);
        comp.setIndustry(industry);
        comp.setCountry(country);
        comp.setAddress(address);
        comp.setActive(true);
        return companyRepository.save(comp);
    }

    @Override
    public List<Company> getAccountCompanies(UUID accountId) {
        return companyRepository.findAllByAccountId(accountId);
    }

    @Override
    public Company getCompany(UUID id) {
        return companyRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Company", "id", id));
    }

    @Override
    @Transactional
    public Company updateCompany(UUID id, String name, String legalName, String industry, String country) {
        var company = getCompany(id);
        if (name != null) company.setName(name);
        if (legalName != null) company.setLegalName(legalName);
        if (industry != null) company.setIndustry(industry);
        if (country != null) company.setCountry(country);
        return companyRepository.save(company);
    }

    @Override
    @Transactional
    public void deactivateCompany(UUID id) {
        var company = getCompany(id);
        company.setActive(false);
        companyRepository.save(company);
    }
}
