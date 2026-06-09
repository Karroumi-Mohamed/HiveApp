package com.hiveapp.platform.client.company.service.impl;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.entity.Company;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.domain.repository.CompanyRepository;
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

import java.util.List;
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
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(companyRepository.findAllByAccountId(accountId)).thenReturn(List.of(new Company()));
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Company result = companyService.createCompany(accountId, "Acme", "Acme LLC", null, null, null, null);

        assertThat(result.getAccount()).isSameAs(account);
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
                requestedAccountId, "Acme", "Acme LLC", null, null, null, null))
                .isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(quotaEnforcer, accountRepository);
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
        return account;
    }
}
