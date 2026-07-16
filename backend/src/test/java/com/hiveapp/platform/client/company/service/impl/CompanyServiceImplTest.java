package com.hiveapp.platform.client.company.service.impl;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.entity.Company;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.domain.repository.CompanyRepository;
import com.hiveapp.platform.client.company.service.CompanyCountryChangeGuard;
import com.hiveapp.platform.client.company.service.CompanyReactivationValidator;
import com.hiveapp.platform.client.company.service.OrganizationInitializer;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.WorkspaceFeature;
import com.hiveapp.shared.exception.ForbiddenException;
import com.hiveapp.shared.quota.QuotaEnforcer;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import com.hiveapp.shared.security.context.HiveAppPermissionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyServiceImplTest {

    @Mock private CompanyRepository companyRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private QuotaEnforcer quotaEnforcer;
    @Mock private CompanyCountryChangeGuard countryChangeGuard;
    @Mock private CompanyReactivationValidator reactivationValidator;
    @Mock private OrganizationInitializer organizationInitializer;

    @InjectMocks
    private CompanyServiceImpl companyService;

    @AfterEach
    void clearContext() {
        HiveAppContextHolder.clearContext();
    }

    @Test
    void createCompanyChecksCompanyQuotaInsideService() {
        UUID accountId = UUID.randomUUID();
        setContext(accountId);
        Account account = account(accountId);
        when(accountRepository.findByIdForQuotaUpdate(accountId)).thenReturn(Optional.of(account));
        when(companyRepository.countByAccountIdAndIsActiveTrue(accountId)).thenReturn(1L);
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = companyService.createCompany(
                accountId, "  Acme  ", " Acme LLC ", " tx-1 ", " Software ", "us", " Main St ", " logo ");

        assertThat(result.company().getAccount()).isSameAs(account);
        assertThat(result.company().getName()).isEqualTo("Acme");
        assertThat(result.company().getTaxId()).isEqualTo("TX-1");
        assertThat(result.company().getCountry()).isEqualTo("US");
        assertThat(result.company().getLogoUrl()).isEqualTo("logo");
        assertThat(result.warnings()).isEmpty();
        verify(organizationInitializer).initialize(result.company());
        ArgumentCaptor<LongSupplier> usageCaptor = ArgumentCaptor.forClass(LongSupplier.class);
        verify(quotaEnforcer).check(
                any(FeatureDefinition.class),
                eq(WorkspaceFeature.COMPANIES),
                eq(accountId),
                usageCaptor.capture());
        assertThat(usageCaptor.getValue().getAsLong()).isEqualTo(1L);
    }

    @Test
    void createCompanyRejectsDifferentAccountBeforeQuotaCheck() {
        UUID currentAccountId = UUID.randomUUID();
        UUID requestedAccountId = UUID.randomUUID();
        setContext(currentAccountId);

        assertThatThrownBy(() -> companyService.createCompany(
                requestedAccountId, "Acme", "Acme LLC", null, null, "US", null, null))
                .isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(quotaEnforcer, accountRepository);
    }

    @Test
    void createCompanyReturnsSameTenantTaxIdWarningWithoutRejectingSave() {
        UUID accountId = UUID.randomUUID();
        setContext(accountId);
        Account account = account(accountId);
        when(accountRepository.findByIdForQuotaUpdate(accountId)).thenReturn(Optional.of(account));
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> {
            Company company = invocation.getArgument(0);
            ReflectionTestUtils.setField(company, "id", UUID.randomUUID());
            return company;
        });
        when(companyRepository.existsByAccountIdAndCountryAndTaxIdAndIdNot(
                eq(accountId), eq("US"), eq("TX-1"), any(UUID.class))).thenReturn(true);

        var result = companyService.createCompany(
                accountId, "Acme", null, "tx-1", null, "us", null, null);

        assertThat(result.warnings()).containsExactly(CompanyServiceImpl.DUPLICATE_TAX_ID_WARNING);
    }

    @Test
    void updateCompanyUsesCountryGuardAndUpdatesAllEditableMetadata() {
        UUID accountId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        setContext(accountId);
        Company company = company(companyId, account(accountId), true);
        company.setCountry("US");
        when(companyRepository.findByIdAndAccountIdForUpdate(companyId, accountId))
                .thenReturn(Optional.of(company));
        when(companyRepository.save(company)).thenReturn(company);

        var result = companyService.updateCompany(
                accountId, companyId, " New Name ", " Legal ", " tax-2 ", " Finance ",
                "ca", " Address ", " https://logo.example/image.png ");

        verify(countryChangeGuard).requireChangeAllowed(company, "CA");
        assertThat(result.company().getName()).isEqualTo("New Name");
        assertThat(result.company().getTaxId()).isEqualTo("TAX-2");
        assertThat(result.company().getCountry()).isEqualTo("CA");
        assertThat(result.company().getAddress()).isEqualTo("Address");
        assertThat(result.company().getLogoUrl()).isEqualTo("https://logo.example/image.png");
    }

    @Test
    void reactivateCompanyRechecksQuotaAndSavedEntitlementsUnderLocks() {
        UUID accountId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        setContext(accountId);
        Account account = account(accountId);
        Company company = company(companyId, account, false);
        when(accountRepository.findByIdForQuotaUpdate(accountId)).thenReturn(Optional.of(account));
        when(companyRepository.findByIdAndAccountIdForUpdate(companyId, accountId))
                .thenReturn(Optional.of(company));
        when(companyRepository.countByAccountIdAndIsActiveTrue(accountId)).thenReturn(1L);
        when(companyRepository.save(company)).thenReturn(company);

        Company result = companyService.reactivateCompany(accountId, companyId);

        assertThat(result.isActive()).isTrue();
        ArgumentCaptor<LongSupplier> usageCaptor = ArgumentCaptor.forClass(LongSupplier.class);
        verify(quotaEnforcer).check(
                any(FeatureDefinition.class),
                eq(WorkspaceFeature.COMPANIES),
                eq(accountId),
                usageCaptor.capture());
        assertThat(usageCaptor.getValue().getAsLong()).isEqualTo(1L);
        verify(reactivationValidator).validate(company);
    }

    private static void setContext(UUID accountId) {
        HiveAppContextHolder.setContext(new HiveAppPermissionContext(
                UUID.randomUUID(), accountId, accountId, null, null, false));
    }

    private static Account account(UUID id) {
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", id);
        account.setName("Acme");
        account.setSlug("acme");
        account.setActive(true);
        return account;
    }

    private static Company company(UUID id, Account account, boolean active) {
        Company company = new Company();
        ReflectionTestUtils.setField(company, "id", id);
        company.setAccount(account);
        company.setName("Acme");
        company.setCountry("US");
        company.setActive(active);
        return company;
    }
}
