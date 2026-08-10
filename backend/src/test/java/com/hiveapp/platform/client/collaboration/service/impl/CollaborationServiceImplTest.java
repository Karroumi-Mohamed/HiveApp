package com.hiveapp.platform.client.collaboration.service.impl;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.entity.Company;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.domain.repository.CompanyRepository;
import com.hiveapp.platform.client.collaboration.domain.constant.CollaborationStatus;
import com.hiveapp.platform.client.collaboration.domain.entity.Collaboration;
import com.hiveapp.platform.client.collaboration.domain.repository.CollaborationPermissionRepository;
import com.hiveapp.platform.client.collaboration.domain.repository.CollaborationRepository;
import com.hiveapp.platform.client.collaboration.dto.CollaborationInitiationResult;
import com.hiveapp.platform.client.collaboration.dto.InitiateCollaborationRequest;
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
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CollaborationServiceImplTest {

    @Mock private CollaborationRepository collaborationRepository;
    @Mock private CollaborationInitiationStore collaborationInitiationStore;
    @Mock private CompanyShareCodeUsageRecorder shareCodeUsageRecorder;
    @Mock private CollaborationPermissionRepository collaborationPermissionRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private PermissionGrantValidator permissionGrantValidator;
    @Mock private PermissionPickerCatalogService permissionPickerCatalogService;
    @Mock private PlanEntitlementService planEntitlementService;
    @Mock private DelegationCeilingService delegationCeilingService;
    @Mock private RegistryCatalogVersionService catalogVersionService;
    @Mock private Clock clock;
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
        Account client = new Account();
        ReflectionTestUtils.setField(client, "id", UUID.randomUUID());
        Company company = new Company();
        ReflectionTestUtils.setField(company, "id", companyId);
        company.setAccount(provider);
        company.setActive(true);
        Collaboration collaboration = new Collaboration();
        ReflectionTestUtils.setField(collaboration, "id", collaborationId);
        collaboration.setClientAccount(client);
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

    @Test
    void identicalRetryNormalizesPurposeWhitespaceAndTreatsCapabilitiesAsAnUnorderedSet() {
        UUID actorId = UUID.randomUUID();
        UUID clientAccountId = UUID.randomUUID();
        UUID providerAccountId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        HiveAppContextHolder.setContext(new HiveAppPermissionContext(
                actorId, clientAccountId, clientAccountId, null, null, false));

        Account client = new Account();
        ReflectionTestUtils.setField(client, "id", clientAccountId);
        client.setActive(true);
        Account provider = new Account();
        ReflectionTestUtils.setField(provider, "id", providerAccountId);
        provider.setActive(true);
        Company company = new Company();
        ReflectionTestUtils.setField(company, "id", companyId);
        company.setAccount(provider);
        company.setActive(true);

        Collaboration existing = new Collaboration();
        ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
        existing.setClientAccount(client);
        existing.setProviderAccount(provider);
        existing.setCompany(company);
        existing.setStatus(CollaborationStatus.PENDING);
        existing.setPurpose("Quarterly payroll support");
        existing.setRequestedPermissionCodes(new LinkedHashSet<>(List.of("capability.a", "capability.b")));

        Permission first = new Permission();
        first.setCode("capability.a");
        Permission second = new Permission();
        second.setCode("capability.b");
        when(clock.instant()).thenReturn(Instant.parse("2026-08-10T12:00:00Z"));
        when(accountRepository.findById(clientAccountId)).thenReturn(Optional.of(client));
        when(companyRepository.findByB2bShareCodeHashAndB2bShareEnabledTrue(anyString()))
                .thenReturn(Optional.of(company));
        when(permissionRepository.findByCode("capability.a")).thenReturn(Optional.of(first));
        when(permissionRepository.findByCode("capability.b")).thenReturn(Optional.of(second));
        when(collaborationRepository
                .findFirstByClientAccountIdAndProviderAccountIdAndCompanyIdAndStatusIn(
                        org.mockito.ArgumentMatchers.eq(clientAccountId),
                        org.mockito.ArgumentMatchers.eq(providerAccountId),
                        org.mockito.ArgumentMatchers.eq(companyId),
                        org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(Optional.of(existing));

        CollaborationInitiationResult result = service.initiateCollaboration(
                clientAccountId,
                new InitiateCollaborationRequest(
                        "share-code",
                        "  Quarterly   payroll\n support  ",
                        new LinkedHashSet<>(List.of("capability.b", "capability.a"))));

        assertThat(result.outcome()).isEqualTo(CollaborationInitiationResult.Outcome.EXISTING_IDENTICAL);
        assertThat(result.collaboration().id()).isEqualTo(existing.getId());
        assertThat(result.collaboration().requestedPermissionCodes())
                .containsExactlyInAnyOrder("capability.a", "capability.b");
        verifyNoInteractions(collaborationInitiationStore);
    }
}
