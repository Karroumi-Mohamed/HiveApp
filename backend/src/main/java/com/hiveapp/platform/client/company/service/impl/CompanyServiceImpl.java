package com.hiveapp.platform.client.company.service.impl;

import com.hiveapp.platform.client.account.domain.entity.Company;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.domain.repository.CompanyRepository;
import com.hiveapp.platform.client.company.service.CompanyService;
import com.hiveapp.platform.registry.definition.CompanyFeature;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.WorkspaceFeature;
import com.hiveapp.platform.registry.definition.service.ClientWorkspaceFeatureService;
import com.hiveapp.shared.exception.ForbiddenException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.quota.QuotaEnforcer;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@PermissionNode(key = CompanyFeature.KEY, description = "Company Management", guard = PermissionNode.Guard.ON)
public class CompanyServiceImpl extends ClientWorkspaceFeatureService implements CompanyService {

    private final CompanyRepository companyRepository;
    private final AccountRepository accountRepository;
    private final QuotaEnforcer quotaEnforcer;

    @Override
    protected FeatureDefinition featureDefinition() {
        return CompanyFeature.definition();
    }

    @Override
    @PermissionNode(key = CompanyFeature.CREATE, description = "Create Company")
    @Transactional
    public Company createCompany(UUID accountId, String name, String legalName, String taxId, String industry, String country, String address) {
        requireCurrentAccount(accountId);
        var account = accountRepository.findByIdForQuotaUpdate(accountId)
            .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));

        quotaEnforcer.check(
                WorkspaceFeature.definition(),
                WorkspaceFeature.COMPANIES,
                accountId,
                () -> companyRepository.countByAccountIdAndIsActiveTrue(accountId)
        );
            
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
    @PermissionNode(key = CompanyFeature.READ_ALL, description = "List Account Companies")
    public List<Company> getAccountCompanies(UUID accountId) {
        requireCurrentAccount(accountId);
        return companyRepository.findAllByAccountId(accountId);
    }

    @Override
    @PermissionNode(key = CompanyFeature.READ_SINGLE, description = "Get Company Details")
    public Company getCompany(UUID accountId, UUID id) {
        return getOwnedCompany(accountId, id);
    }

    @Override
    @Transactional
    @PermissionNode(key = CompanyFeature.UPDATE, description = "Update Company")
    public Company updateCompany(UUID accountId, UUID id, String name, String legalName, String industry, String country) {
        var company = getOwnedCompany(accountId, id);
        if (name != null) company.setName(name);
        if (legalName != null) company.setLegalName(legalName);
        if (industry != null) company.setIndustry(industry);
        if (country != null) company.setCountry(country);
        return companyRepository.save(company);
    }

    @Override
    @Transactional
    @PermissionNode(key = CompanyFeature.DELETE, description = "Deactivate Company")
    public void deactivateCompany(UUID accountId, UUID id) {
        var company = getOwnedCompany(accountId, id);
        company.setActive(false);
        companyRepository.save(company);
    }

    private Company getOwnedCompany(UUID accountId, UUID id) {
        requireCurrentAccount(accountId);
        requireB2bTargetCompany(id);
        var company = companyRepository.findByIdAndAccountId(id, accountId)
            .orElseThrow(() -> new ResourceNotFoundException("Company", "id", id));
        return company;
    }

    private void requireB2bTargetCompany(UUID companyId) {
        var context = HiveAppContextHolder.getContext();
        if (context != null && context.isB2B() && !companyId.equals(context.targetCompanyId())) {
            throw new ForbiddenException("B2B access is limited to the collaboration company");
        }
    }

    private void requireCurrentAccount(UUID accountId) {
        UUID currentAccountId = HiveAppContextHolder.getContext().currentAccountId();
        if (!accountId.equals(currentAccountId)) {
            throw new ForbiddenException("Company does not belong to your account");
        }
    }
}
