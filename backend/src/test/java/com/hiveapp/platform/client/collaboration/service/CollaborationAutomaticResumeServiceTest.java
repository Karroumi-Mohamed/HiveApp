package com.hiveapp.platform.client.collaboration.service;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.company.domain.entity.Company;
import com.hiveapp.platform.client.collaboration.domain.constant.CollaborationStatus;
import com.hiveapp.platform.client.collaboration.domain.entity.Collaboration;
import com.hiveapp.platform.client.collaboration.domain.repository.CollaborationRepository;
import com.hiveapp.platform.client.plan.service.PlanEntitlementService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollaborationAutomaticResumeServiceTest {

    @Test
    void resumesOnlyDueCollaborationsWhoseFullScopeIsActive() {
        Instant now = Instant.parse("2026-08-10T18:00:00Z");
        Collaboration active = suspendedCollaboration(true, now.minusSeconds(1));
        Collaboration inactive = suspendedCollaboration(false, now.minusSeconds(1));
        Collaboration unentitled = suspendedCollaboration(true, now.minusSeconds(1));
        CollaborationRepository repository = mock(CollaborationRepository.class);
        PlanEntitlementService entitlements = mock(PlanEntitlementService.class);
        when(repository.findDueAutomaticResumesForUpdate(CollaborationStatus.SUSPENDED, now))
                .thenReturn(List.of(active, inactive, unentitled));
        when(entitlements.isPermissionEntitled(any(UUID.class), eq("platform.b2b.resume")))
                .thenReturn(true);
        when(entitlements.isPermissionEntitled(
                unentitled.getProviderAccount().getId(), "platform.b2b.resume"))
                .thenReturn(false);
        CollaborationAutomaticResumeService service = new CollaborationAutomaticResumeService(
                repository, entitlements, Clock.fixed(now, ZoneOffset.UTC));

        assertThat(service.resumeDueCollaborations()).isEqualTo(1);

        assertThat(active.getStatus()).isEqualTo(CollaborationStatus.ACTIVE);
        assertThat(active.getResumedAt()).isEqualTo(now);
        assertThat(active.getAutomaticResumeAt()).isNull();
        assertThat(active.getLifecycleReason()).contains("Automatically resumed");
        assertThat(inactive.getStatus()).isEqualTo(CollaborationStatus.SUSPENDED);
        assertThat(inactive.getAutomaticResumeAt()).isEqualTo(now.minusSeconds(1));
        assertThat(unentitled.getStatus()).isEqualTo(CollaborationStatus.SUSPENDED);
        assertThat(unentitled.getAutomaticResumeAt()).isEqualTo(now.minusSeconds(1));
        verify(repository).findDueAutomaticResumesForUpdate(CollaborationStatus.SUSPENDED, now);
    }

    private Collaboration suspendedCollaboration(boolean clientActive, Instant automaticResumeAt) {
        Account client = new Account();
        client.setActive(clientActive);
        Account provider = new Account();
        ReflectionTestUtils.setField(provider, "id", UUID.randomUUID());
        provider.setActive(true);
        Company company = new Company();
        company.setAccount(provider);
        company.setActive(true);
        Collaboration collaboration = new Collaboration();
        collaboration.setClientAccount(client);
        collaboration.setProviderAccount(provider);
        collaboration.setCompany(company);
        collaboration.setStatus(CollaborationStatus.SUSPENDED);
        collaboration.setAutomaticResumeAt(automaticResumeAt);
        return collaboration;
    }
}
