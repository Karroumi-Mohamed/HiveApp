package com.hiveapp.platform.client.company.service;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.entity.Company;
import com.hiveapp.platform.client.collaboration.domain.constant.CollaborationStatus;
import com.hiveapp.platform.client.collaboration.domain.entity.CollaborationPermission;
import com.hiveapp.platform.client.collaboration.domain.repository.CollaborationPermissionRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberPermissionOverrideRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRoleRepository;
import com.hiveapp.platform.client.plan.service.PlanEntitlementService;
import com.hiveapp.platform.registry.domain.entity.Permission;
import com.hiveapp.shared.exception.InvalidStateException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompanyReactivationValidatorTest {

    @Test
    void unavailablePreservedB2bGrantBlocksReactivation() {
        UUID accountId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", accountId);
        Company company = new Company();
        ReflectionTestUtils.setField(company, "id", companyId);
        company.setAccount(account);
        company.setName("Acme");
        company.setCountry("US");

        Permission permission = new Permission();
        permission.setCode("platform.company.read_single");
        CollaborationPermission grant = new CollaborationPermission();
        grant.setPermission(permission);

        MemberRoleRepository roles = mock(MemberRoleRepository.class);
        MemberPermissionOverrideRepository overrides = mock(MemberPermissionOverrideRepository.class);
        CollaborationPermissionRepository collaborations = mock(CollaborationPermissionRepository.class);
        PlanEntitlementService entitlements = mock(PlanEntitlementService.class);
        when(roles.findAllByCompanyId(companyId)).thenReturn(List.of());
        when(overrides.findAllByCompanyId(companyId)).thenReturn(List.of());
        when(collaborations.findAllByCollaborationCompanyIdAndCollaborationStatus(
                companyId, CollaborationStatus.ACTIVE)).thenReturn(List.of(grant));
        when(entitlements.isPermissionEntitled(accountId, permission.getCode())).thenReturn(false);

        CompanyReactivationValidator validator = new CompanyReactivationValidator(
                roles, overrides, collaborations, entitlements);

        assertThatThrownBy(() -> validator.validate(company))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining(permission.getCode());
    }
}
