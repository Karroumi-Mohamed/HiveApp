package com.hiveapp.platform.client.company.service.impl;

import com.hiveapp.platform.client.account.domain.entity.Company;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.domain.repository.CompanyRepository;
import com.hiveapp.platform.client.company.service.CompanyService;
import com.hiveapp.platform.client.company.service.CompanyCountryChangeGuard;
import com.hiveapp.platform.client.company.service.CompanyMutationResult;
import com.hiveapp.platform.client.company.service.CompanyReactivationValidator;
import com.hiveapp.platform.registry.definition.CompanyFeature;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.WorkspaceFeature;
import com.hiveapp.platform.registry.definition.service.ClientWorkspaceFeatureService;
import com.hiveapp.shared.exception.ForbiddenException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.quota.QuotaEnforcer;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@PermissionNode(key = CompanyFeature.KEY, description = "Company Management", guard = PermissionNode.Guard.ON)
public class CompanyServiceImpl extends ClientWorkspaceFeatureService implements CompanyService {

    public static final String DUPLICATE_TAX_ID_WARNING =
            "Another company in this account and country uses the same tax ID.";

    private final CompanyRepository companyRepository;
    private final AccountRepository accountRepository;
    private final QuotaEnforcer quotaEnforcer;
    private final CompanyCountryChangeGuard countryChangeGuard;
    private final CompanyReactivationValidator reactivationValidator;

    @Override
    protected FeatureDefinition featureDefinition() {
        return CompanyFeature.definition();
    }

    @Override
    @PermissionNode(key = CompanyFeature.CREATE, description = "Create Company")
    @Transactional
    public CompanyMutationResult createCompany(
            UUID accountId,
            String name,
            String legalName,
            String taxId,
            String industry,
            String country,
            String address,
            String logoUrl) {
        requireCurrentAccount(accountId);
        var account = accountRepository.findByIdForQuotaUpdate(accountId)
            .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        requireActiveAccount(account);

        quotaEnforcer.check(
                WorkspaceFeature.definition(),
                WorkspaceFeature.COMPANIES,
                accountId,
                () -> companyRepository.countByAccountIdAndIsActiveTrue(accountId)
        );
            
        String normalizedCountry = normalizeCountry(country);
        String normalizedTaxId = normalizeTaxId(taxId);
        Company comp = new Company();
        comp.setAccount(account);
        comp.setName(normalizeRequired(name, "Company name"));
        comp.setLegalName(normalizeOptional(legalName));
        comp.setTaxId(normalizedTaxId);
        comp.setIndustry(normalizeOptional(industry));
        comp.setCountry(normalizedCountry);
        comp.setAddress(normalizeOptional(address));
        comp.setLogoUrl(normalizeOptional(logoUrl));
        comp.setActive(true);
        var saved = companyRepository.save(comp);
        return new CompanyMutationResult(
                saved,
                taxIdWarnings(accountId, normalizedCountry, normalizedTaxId, saved.getId()));
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
    public CompanyMutationResult updateCompany(
            UUID accountId,
            UUID id,
            String name,
            String legalName,
            String taxId,
            String industry,
            String country,
            String address,
            String logoUrl) {
        var company = getOwnedCompanyForUpdate(accountId, id);
        if (name != null) company.setName(normalizeRequired(name, "Company name"));
        if (legalName != null) company.setLegalName(normalizeOptional(legalName));
        if (taxId != null) company.setTaxId(normalizeTaxId(taxId));
        if (industry != null) company.setIndustry(normalizeOptional(industry));
        if (country != null) {
            String normalizedCountry = normalizeCountry(country);
            countryChangeGuard.requireChangeAllowed(company, normalizedCountry);
            company.setCountry(normalizedCountry);
        }
        if (address != null) company.setAddress(normalizeOptional(address));
        if (logoUrl != null) company.setLogoUrl(normalizeOptional(logoUrl));
        var saved = companyRepository.save(company);
        return new CompanyMutationResult(
                saved,
                taxIdWarnings(accountId, saved.getCountry(), saved.getTaxId(), saved.getId()));
    }

    @Override
    @Transactional
    @PermissionNode(key = CompanyFeature.DELETE, description = "Deactivate Company")
    public void deactivateCompany(UUID accountId, UUID id) {
        var company = getOwnedCompanyForUpdate(accountId, id);
        if (!company.isActive()) {
            return;
        }
        company.setActive(false);
        companyRepository.save(company);
    }

    @Override
    @Transactional
    @PermissionNode(key = CompanyFeature.REACTIVATE, description = "Reactivate Company")
    public Company reactivateCompany(UUID accountId, UUID id) {
        requireCurrentAccount(accountId);
        requireB2bTargetCompany(id);
        var account = accountRepository.findByIdForQuotaUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        requireActiveAccount(account);
        var company = companyRepository.findByIdAndAccountIdForUpdate(id, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", "id", id));
        if (company.isActive()) {
            return company;
        }

        quotaEnforcer.check(
                WorkspaceFeature.definition(),
                WorkspaceFeature.COMPANIES,
                accountId,
                () -> companyRepository.countByAccountIdAndIsActiveTrue(accountId));
        reactivationValidator.validate(company);
        company.setActive(true);
        return companyRepository.save(company);
    }

    private Company getOwnedCompany(UUID accountId, UUID id) {
        requireCurrentAccount(accountId);
        requireB2bTargetCompany(id);
        var company = companyRepository.findByIdAndAccountId(id, accountId)
            .orElseThrow(() -> new ResourceNotFoundException("Company", "id", id));
        return company;
    }

    private Company getOwnedCompanyForUpdate(UUID accountId, UUID id) {
        requireCurrentAccount(accountId);
        requireB2bTargetCompany(id);
        return companyRepository.findByIdAndAccountIdForUpdate(id, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", "id", id));
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

    private void requireActiveAccount(com.hiveapp.platform.client.account.domain.entity.Account account) {
        if (!account.isActive()) {
            throw new InvalidStateException("Company lifecycle changes are unavailable while the account is suspended");
        }
    }

    private List<String> taxIdWarnings(
            UUID accountId,
            String country,
            String taxId,
            UUID excludedCompanyId) {
        if (taxId == null) {
            return List.of();
        }
        boolean duplicate = excludedCompanyId == null
                ? companyRepository.existsByAccountIdAndCountryAndTaxId(accountId, country, taxId)
                : companyRepository.existsByAccountIdAndCountryAndTaxIdAndIdNot(
                        accountId, country, taxId, excludedCompanyId);
        return duplicate ? List.of(DUPLICATE_TAX_ID_WARNING) : List.of();
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new InvalidRequestException(fieldName + " is required");
        }
        return normalized;
    }

    private String normalizeCountry(String value) {
        String normalized = normalizeRequired(value, "Company country").toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{2}")) {
            throw new InvalidRequestException("Company country must be a two-letter country code");
        }
        return normalized;
    }

    private String normalizeTaxId(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
