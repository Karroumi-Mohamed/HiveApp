package com.hiveapp.platform.client.company.service;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.entity.Company;
import com.hiveapp.shared.exception.InvalidStateException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompanyCountryChangeGuardTest {

    @Test
    void countryDependentDomainCanBlockOrdinaryCountryEdit() {
        UUID companyId = UUID.randomUUID();
        Company company = new Company();
        ReflectionTestUtils.setField(company, "id", companyId);
        company.setAccount(new Account());
        company.setName("Acme");
        company.setCountry("US");
        CompanyCountryDependency payroll = mock(CompanyCountryDependency.class);
        when(payroll.blockingReason(companyId, "US", "CA"))
                .thenReturn(Optional.of("payroll records"));

        CompanyCountryChangeGuard guard = new CompanyCountryChangeGuard(List.of(payroll));

        assertThatThrownBy(() -> guard.requireChangeAllowed(company, "CA"))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("payroll records");
    }
}
