package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionFeatureSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrides;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.entity.Permission;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanEntitlementServiceTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private SubscriptionSnapshotReader subscriptionSnapshotReader;
    private static final Instant NOW = Instant.parse("2026-08-31T12:00:00Z");

    private PlanEntitlementService service;
    private UUID accountId;
    private UUID planId;

    @BeforeEach
    void setUp() {
        service = new PlanEntitlementService(
                subscriptionRepository,
                permissionRepository,
                subscriptionSnapshotReader,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        accountId = UUID.randomUUID();
        planId = UUID.randomUUID();
    }

    @Test
    void activePlanFeatureEntitlesPermission() {
        Subscription subscription = subscription(SubscriptionStatus.ACTIVE, null);
        current(subscription);
        when(permissionRepository.findByCode("platform.company.create"))
                .thenReturn(Optional.of(permission("platform.company.create", "platform.company")));
        when(subscriptionSnapshotReader.read(subscription.getEntitlementSnapshot()))
                .thenReturn(Optional.of(subscription.getEntitlementSnapshot()));

        assertThat(service.isPermissionEntitled(accountId, "platform.company.create")).isTrue();
    }

    @Test
    void unexpiredTrialPlanFeatureEntitlesPermission() {
        current(subscription(SubscriptionStatus.TRIALING, NOW.plusSeconds(86_400)));
        when(permissionRepository.findByCode("platform.company.create"))
                .thenReturn(Optional.of(permission("platform.company.create", "platform.company")));
        when(subscriptionSnapshotReader.read(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));

        assertThat(service.isPermissionEntitled(accountId, "platform.company.create")).isTrue();
    }

    @Test
    void subscriptionSnapshotEntitlesPermissionWithoutLivePlanFeature() {
        Subscription subscription = subscription(SubscriptionStatus.ACTIVE, null);
        current(subscription);
        when(permissionRepository.findByCode("platform.company.create"))
                .thenReturn(Optional.of(permission("platform.company.create", "platform.company")));
        when(subscriptionSnapshotReader.read(subscription.getEntitlementSnapshot()))
                .thenReturn(Optional.of(new SubscriptionEntitlementSnapshot(
                        "FREE",
                        java.math.BigDecimal.ZERO,
                        "USD",
                        BillingCycle.MONTHLY,
                        List.of(new SubscriptionFeatureSnapshot("platform.company", List.of())),
                        List.of())));

        assertThat(service.isPermissionEntitled(accountId, "platform.company.create")).isTrue();
    }

    @Test
    void expiredSubscriptionDoesNotEntitlePermission() {
        current(subscription(SubscriptionStatus.ACTIVE, NOW.minusSeconds(60)));

        assertThat(service.isPermissionEntitled(accountId, "platform.company.create")).isFalse();
    }

    @Test
    void missingSnapshotFailsClosedEvenWhenAnOverrideExists() {
        current(subscriptionWithoutSnapshot());
        when(permissionRepository.findByCode("platform.company.create"))
                .thenReturn(Optional.of(permission("platform.company.create", "platform.company")));
        when(subscriptionSnapshotReader.read(null)).thenReturn(Optional.empty());

        assertThat(service.isPermissionEntitled(accountId, "platform.company.create")).isFalse();
    }

    @Test
    void missingSubscriptionDoesNotEntitlePermission() {
        current(null);

        assertThat(service.isPermissionEntitled(accountId, "platform.company.create")).isFalse();
    }

    @Test
    void resolvesAllEntitledFeaturesFromOneSubscriptionSnapshot() {
        Subscription subscription = subscription(SubscriptionStatus.ACTIVE, null);
        current(subscription);
        when(subscriptionSnapshotReader.read(subscription.getEntitlementSnapshot()))
                .thenReturn(Optional.of(new SubscriptionEntitlementSnapshot(
                        "PRO", java.math.BigDecimal.ZERO, "USD",
                        BillingCycle.MONTHLY,
                        List.of(
                                new SubscriptionFeatureSnapshot("platform.company", List.of()),
                                new SubscriptionFeatureSnapshot("platform.staff", List.of()),
                                new SubscriptionFeatureSnapshot("platform.organization", List.of())),
                        List.of())));

        assertThat(service.entitledFeatureCodes(accountId))
                .containsExactlyInAnyOrder(
                        "platform.company", "platform.staff", "platform.organization");
        verifyNoInteractions(permissionRepository);
    }

    @Test
    void pastDueSubscriptionRemainsEntitledInsidePersistedGrace() {
        Subscription subscription = subscription(SubscriptionStatus.PAST_DUE, NOW.minusSeconds(60));
        subscription.setPastDueAt(NOW.minusSeconds(60));
        subscription.setGraceEndsAt(NOW.plusSeconds(60));
        current(subscription);
        when(permissionRepository.findByCode("platform.company.create"))
                .thenReturn(Optional.of(permission("platform.company.create", "platform.company")));
        when(subscriptionSnapshotReader.read(subscription.getEntitlementSnapshot()))
                .thenReturn(Optional.of(subscription.getEntitlementSnapshot()));

        assertThat(service.isPermissionEntitled(accountId, "platform.company.create")).isTrue();
    }

    @Test
    void pastDueSubscriptionFailsClosedAtGraceDeadline() {
        current(null);

        assertThat(service.isPermissionEntitled(accountId, "platform.company.create")).isFalse();
        verifyNoInteractions(permissionRepository);
    }

    private void current(Subscription subscription) {
        when(subscriptionRepository.findEntitledAt(
                accountId,
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING),
                SubscriptionStatus.PAST_DUE,
                NOW)).thenReturn(Optional.ofNullable(subscription));
    }

    private Subscription subscription(SubscriptionStatus status, Instant currentPeriodEnd) {
        Plan plan = new Plan();
        ReflectionTestUtils.setField(plan, "id", planId);
        Subscription subscription = new Subscription();
        subscription.setPlan(plan);
        subscription.setStatus(status);
        subscription.setCurrentPeriodEnd(currentPeriodEnd);
        subscription.setCustomOverrides(SubscriptionOverrides.empty());
        subscription.setEntitlementSnapshot(new SubscriptionEntitlementSnapshot(
                "FREE", java.math.BigDecimal.ZERO, "USD", BillingCycle.MONTHLY,
                List.of(new SubscriptionFeatureSnapshot("platform.company", List.of())), List.of()));
        return subscription;
    }

    private Subscription subscriptionWithoutSnapshot() {
        Subscription subscription = subscription(SubscriptionStatus.ACTIVE, null);
        subscription.setEntitlementSnapshot(null);
        return subscription;
    }

    private Permission permission(String code, String featureCode) {
        Feature feature = new Feature();
        feature.setCode(featureCode);
        Permission permission = new Permission();
        permission.setCode(code);
        permission.setFeature(feature);
        return permission;
    }

    private Feature feature(String featureCode) {
        Feature feature = new Feature();
        feature.setCode(featureCode);
        return feature;
    }
}
