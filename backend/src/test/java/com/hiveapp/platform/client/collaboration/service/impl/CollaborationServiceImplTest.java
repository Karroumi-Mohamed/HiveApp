package com.hiveapp.platform.client.collaboration.service.impl;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.entity.Company;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.domain.repository.CompanyRepository;
import com.hiveapp.platform.client.collaboration.domain.constant.CollaborationStatus;
import com.hiveapp.platform.client.collaboration.domain.entity.Collaboration;
import com.hiveapp.platform.client.collaboration.domain.repository.CollaborationPermissionRepository;
import com.hiveapp.platform.client.collaboration.domain.repository.CollaborationRepository;
import com.hiveapp.platform.client.plan.service.PlanEntitlementService;
import com.hiveapp.platform.registry.definition.PermissionGrantValidator;
import com.hiveapp.platform.registry.domain.entity.Permission;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.platform.registry.service.PermissionPickerCatalogService;
import com.hiveapp.platform.registry.service.RegistryCatalogVersionService;
import com.hiveapp.shared.exception.ForbiddenException;
import com.hiveapp.shared.security.DelegationCeilingService;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import com.hiveapp.shared.security.context.HiveAppPermissionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollaborationServiceImplTest {

    @Mock private CollaborationRepository collaborationRepository;
    @Mock private CollaborationPermissionRepository collaborationPermissionRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private PermissionGrantValidator permissionGrantValidator;
    @Mock private PermissionPickerCatalogService permissionPickerCatalogService;
    @Mock private PlanEntitlementService planEntitlementService;
    @Mock private DelegationCeilingService delegationCeilingService;
    @Mock private RegistryCatalogVersionService catalogVersionService;
    @InjectMocks private CollaborationServiceImpl service;

    @AfterEach
    void clearContext() {
        HiveAppContextHolder.clearContext();
    }

    @Test
    void providerMemberCannotDelegateB2bPermissionAboveTheirCompanyCeiling() {
        UUID actorId = UUID.randomUUID();
        UUID providerAccountId = UUID.randomUUID();
        UUID collaborationId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        String permissionCode = "platform.company.read_single";
        HiveAppContextHolder.setContext(new HiveAppPermissionContext(
                actorId, providerAccountId, providerAccountId, null, null, false));

        Account provider = new Account();
        ReflectionTestUtils.setField(provider, "id", providerAccountId);
        Company company = new Company();
        ReflectionTestUtils.setField(company, "id", companyId);
        company.setAccount(provider);
        company.setActive(true);
        Collaboration collaboration = new Collaboration();
        ReflectionTestUtils.setField(collaboration, "id", collaborationId);
        collaboration.setProviderAccount(provider);
        collaboration.setCompany(company);
        collaboration.setStatus(CollaborationStatus.ACTIVE);
        Permission permission = new Permission();
        permission.setCode(permissionCode);

        when(collaborationRepository.findByIdAndProviderAccountId(collaborationId, providerAccountId))
                .thenReturn(Optional.of(collaboration));
        when(permissionRepository.findByCode(permissionCode)).thenReturn(Optional.of(permission));
        when(planEntitlementService.isPermissionEntitled(providerAccountId, permissionCode)).thenReturn(true);
        org.mockito.Mockito.doThrow(new ForbiddenException("above provider ceiling"))
                .when(delegationCeilingService)
                .requireActorCanDelegate(providerAccountId, companyId, List.of(permissionCode));

        assertThatThrownBy(() -> service.grantPermission(
                providerAccountId, collaborationId, permissionCode, "snapshot:1"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("provider ceiling");
        verify(collaborationPermissionRepository, never())
                .save(org.mockito.ArgumentMatchers.any());
    }
}
