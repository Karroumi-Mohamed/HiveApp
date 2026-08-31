package com.hiveapp.platform.client.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.member.service.MemberAccessSessionService;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPreviewKind;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionLifecycleAction;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionSuspensionCause;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionLifecycleEvent;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeOperationRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionLifecycleEventRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.dto.SubscriptionLifecycleModels;
import com.hiveapp.platform.registry.service.RegistryCatalogVersionService;
import com.hiveapp.shared.exception.InvalidStateException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SubscriptionLifecycleAdminServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-31T12:00:00Z");

    @Mock private SubscriptionRepository subscriptions;
    @Mock private SubscriptionLifecycleEventRepository events;
    @Mock private SubscriptionChangeOperationRepository operations;
    @Mock private SubscriptionCheckoutService checkouts;
    @Mock private SubscriptionLifecycleManager lifecycle;
    @Mock private MemberAccessSessionService memberSessions;
    @Mock private CommercialPreviewTokenService evidence;
    @Mock private CommercialCatalogVersionService catalogVersions;
    @Mock private RegistryCatalogVersionService registryVersions;

    private SubscriptionLifecycleAdminService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionLifecycleAdminService(
                subscriptions, events, operations, checkouts, lifecycle, memberSessions,
                evidence, catalogVersions, registryVersions,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void operatorSuspensionRevokesSessionsAndRecordsReasonedHistory() {
        stubCatalogVersions();
        Subscription subscription = subscription(SubscriptionStatus.ACTIVE);
        stubCurrent(subscription);
        when(operations.existsByAccountIdAndStatusIn(
                eq(subscription.getAccount().getId()), any())).thenReturn(false);
        when(evidence.requireValid(
                eq("signed"), eq(CommercialPreviewKind.SUBSCRIPTION_LIFECYCLE),
                eq(subscription.getId()), eq(4L), any(), eq(7L), eq("registry-7"),
                anyString(), any())).thenReturn(
                        new CommercialPreviewTokenService.VerifiedEvidence(NOW, NOW.plusSeconds(300)));
        when(events.saveAndFlush(any())).thenAnswer(invocation -> {
            SubscriptionLifecycleEvent event = invocation.getArgument(0);
            ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
            return event;
        });

        var result = service.suspend(
                subscription.getAccount().getId(), UUID.randomUUID(),
                new SubscriptionLifecycleModels.ApplyRequest(
                        "signed", "Security investigation", null));

        assertThat(result.status()).isEqualTo(SubscriptionStatus.SUSPENDED);
        assertThat(subscription.getSuspensionCause()).isEqualTo(SubscriptionSuspensionCause.OPERATOR);
        assertThat(subscription.getSuspendedFromStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getSuspensionReason()).isEqualTo("Security investigation");
        verify(memberSessions).revokeAllForAccount(subscription.getAccount().getId());
        verify(events).saveAndFlush(any());
    }

    @Test
    void collectionSuspensionCannotUseOperatorRestore() {
        Subscription subscription = subscription(SubscriptionStatus.SUSPENDED);
        subscription.setSuspensionCause(SubscriptionSuspensionCause.COLLECTION);
        subscription.setSuspendedFromStatus(SubscriptionStatus.PAST_DUE);
        subscription.setGraceEndsAt(NOW.minusSeconds(1));
        stubCurrent(subscription);

        assertThatThrownBy(() -> service.restore(
                subscription.getAccount().getId(), UUID.randomUUID(),
                new SubscriptionLifecycleModels.ApplyRequest("signed", "Waive payment", null)))
                .isInstanceOf(InvalidStateException.class);
        verify(evidence, never()).requireValid(
                anyString(), any(), any(), anyLong(), any(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void graceExtensionReopensOnlyThePersistedRecoveryWindow() {
        stubCatalogVersions();
        Subscription subscription = subscription(SubscriptionStatus.SUSPENDED);
        subscription.setSuspensionCause(SubscriptionSuspensionCause.COLLECTION);
        subscription.setSuspendedFromStatus(SubscriptionStatus.PAST_DUE);
        subscription.setPastDueAt(NOW.minusSeconds(7_200));
        subscription.setGraceEndsAt(NOW.minusSeconds(1));
        stubCurrent(subscription);
        Instant extended = NOW.plusSeconds(86_400);
        when(evidence.requireValid(
                eq("signed"), any(), any(), anyLong(), any(), anyLong(), anyString(),
                anyString(), any())).thenReturn(
                        new CommercialPreviewTokenService.VerifiedEvidence(NOW, NOW.plusSeconds(300)));
        when(events.saveAndFlush(any())).thenAnswer(invocation -> {
            SubscriptionLifecycleEvent event = invocation.getArgument(0);
            ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
            return event;
        });

        service.extendGrace(
                subscription.getAccount().getId(), UUID.randomUUID(),
                new SubscriptionLifecycleModels.ApplyRequest(
                        "signed", "Contractual payment delay", extended));

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(subscription.getGraceEndsAt()).isEqualTo(extended);
        assertThat(subscription.getSuspensionCause()).isNull();
        verify(memberSessions, never()).revokeAllForAccount(any());
    }

    private void stubCurrent(Subscription subscription) {
        UUID accountId = subscription.getAccount().getId();
        when(subscriptions.findCurrentByAccountId(accountId)).thenReturn(Optional.of(subscription));
        when(subscriptions.findCurrentForLifecycleUpdate(
                eq(subscription.getId()), eq(accountId), any())).thenReturn(Optional.of(subscription));
    }

    private void stubCatalogVersions() {
        when(catalogVersions.currentRevision()).thenReturn(7L);
        when(registryVersions.currentVersion()).thenReturn("registry-7");
    }

    private Subscription subscription(SubscriptionStatus status) {
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        Plan plan = new Plan();
        ReflectionTestUtils.setField(plan, "id", UUID.randomUUID());
        Subscription subscription = new Subscription();
        ReflectionTestUtils.setField(subscription, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(subscription, "version", 4L);
        subscription.setAccount(account);
        subscription.setPlan(plan);
        subscription.setStatus(status);
        subscription.setCurrentPeriodStart(NOW.minusSeconds(86_400));
        subscription.setCurrentPeriodEnd(NOW.plusSeconds(86_400));
        return subscription;
    }
}
