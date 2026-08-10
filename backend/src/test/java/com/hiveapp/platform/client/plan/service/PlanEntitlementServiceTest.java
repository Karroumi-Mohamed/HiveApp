package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import com.hiveapp.platform.client.plan.domain.entity.AddOn;
import com.hiveapp.platform.client.plan.domain.entity.AddOnFeature;
import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.AddOnFeatureRepository;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanEntitlementServiceTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PlanFeatureRepository planFeatureRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private SubscriptionOverrideReader subscriptionOverrideReader;
    @Mock private SubscriptionSnapshotReader subscriptionSnapshotReader;
    @Mock private AddOnRepository addOnRepository;
    @Mock private AddOnFeatureRepository addOnFeatureRepository;

    private PlanEntitlementService service;
    private UUID accountId;
    private UUID planId;

    @BeforeEach
    void setUp() {
        service = new PlanEntitlementService(
                subscriptionRepository,
                planFeatureRepository,
                permissionRepository,
                subscriptionOverrideReader,
                subscriptionSnapshotReader,
                addOnRepository,
                addOnFeatureRepository
        );
        accountId = UUID.randomUUID();
        planId = UUID.randomUUID();
    }

    @Test
    void activePlanFeatureEntitlesPermission() {
        when(subscriptionRepository.findActiveByAccountId(accountId))
                .thenReturn(Optional.of(subscription(SubscriptionStatus.ACTIVE, null, null)));
        when(permissionRepository.findByCode("platform.company.create"))
                .thenReturn(Optional.of(permission("platform.company.create", "platform.company")));
        when(subscriptionSnapshotReader.read(null)).thenReturn(Optional.empty());
        when(planFeatureRepository.findByPlanIdAndFeature_Code(planId, "platform.company"))
                .thenReturn(Optional.of(includedPlanFeature("platform.company")));

        assertThat(service.isPermissionEntitled(accountId, "platform.company.create")).isTrue();
    }

    @Test
    void unexpiredTrialPlanFeatureEntitlesPermission() {
        when(subscriptionRepository.findActiveByAccountId(accountId)).thenReturn(Optional.empty());
        when(subscriptionRepository.findByAccountIdAndStatus(accountId, SubscriptionStatus.TRIALING))
                .thenReturn(Optional.of(subscription(
                        SubscriptionStatus.TRIALING,
                        LocalDateTime.now().plusDays(1),
                        null
                )));
        when(permissionRepository.findByCode("platform.company.create"))
                .thenReturn(Optional.of(permission("platform.company.create", "platform.company")));
        when(subscriptionSnapshotReader.read(null)).thenReturn(Optional.empty());
        when(planFeatureRepository.findByPlanIdAndFeature_Code(planId, "platform.company"))
                .thenReturn(Optional.of(includedPlanFeature("platform.company")));

        assertThat(service.isPermissionEntitled(accountId, "platform.company.create")).isTrue();
    }

    @Test
    void subscriptionSnapshotEntitlesPermissionWithoutLivePlanFeature() {
        Subscription subscription = subscription(SubscriptionStatus.ACTIVE, null, null);
        subscription.setEntitlementSnapshot("{\"snapshot\":true}");
        when(subscriptionRepository.findActiveByAccountId(accountId)).thenReturn(Optional.of(subscription));
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
        verifyNoInteractions(planFeatureRepository);
    }

    @Test
    void expiredSubscriptionDoesNotEntitlePermission() {
        when(subscriptionRepository.findActiveByAccountId(accountId))
                .thenReturn(Optional.of(subscription(
                        SubscriptionStatus.ACTIVE,
                        LocalDateTime.now().minusMinutes(1),
                        null
                )));

        assertThat(service.isPermissionEntitled(accountId, "platform.company.create")).isFalse();
        verifyNoInteractions(planFeatureRepository);
    }

    @Test
    void selectedAddOnEntitlesPermissionWhenLegacySnapshotIsMissing() {
        String overrides = "{\"addOnCodes\":[\"COMPANY_MODULE\"]}";
        when(subscriptionRepository.findActiveByAccountId(accountId))
                .thenReturn(Optional.of(subscription(SubscriptionStatus.ACTIVE, null, overrides)));
        when(permissionRepository.findByCode("platform.company.create"))
                .thenReturn(Optional.of(permission("platform.company.create", "platform.company")));
        when(subscriptionSnapshotReader.read(null)).thenReturn(Optional.empty());
        when(planFeatureRepository.findByPlanIdAndFeature_Code(planId, "platform.company"))
                .thenReturn(Optional.empty());
        when(subscriptionOverrideReader.read(overrides))
                .thenReturn(new SubscriptionOverrides(Set.of("COMPANY_MODULE"), List.of()));
        AddOn addOn = new AddOn();
        ReflectionTestUtils.setField(addOn, "id", UUID.randomUUID());
        addOn.setCode("COMPANY_MODULE");
        AddOnFeature addOnFeature = new AddOnFeature();
        addOnFeature.setFeature(feature("platform.company"));
        when(addOnRepository.findAllByCodeIn(Set.of("COMPANY_MODULE"))).thenReturn(List.of(addOn));
        when(addOnFeatureRepository.findAllByAddOnId(addOn.getId())).thenReturn(List.of(addOnFeature));

        assertThat(service.isPermissionEntitled(accountId, "platform.company.create")).isTrue();
    }

    @Test
    void missingSubscriptionDoesNotEntitlePermission() {
        when(subscriptionRepository.findActiveByAccountId(accountId)).thenReturn(Optional.empty());
        when(subscriptionRepository.findByAccountIdAndStatus(accountId, SubscriptionStatus.TRIALING))
                .thenReturn(Optional.empty());

        assertThat(service.isPermissionEntitled(accountId, "platform.company.create")).isFalse();
        verifyNoInteractions(planFeatureRepository);
    }

    @Test
    void resolvesAllEntitledFeaturesFromOneSubscriptionSnapshot() {
        String overrides = "{\"addOnCodes\":[\"ORGANIZATION_MODULE\"]}";
        Subscription subscription = subscription(SubscriptionStatus.ACTIVE, null, overrides);
        subscription.setEntitlementSnapshot("{\"snapshot\":true}");
        when(subscriptionRepository.findActiveByAccountId(accountId)).thenReturn(Optional.of(subscription));
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
        verifyNoInteractions(permissionRepository, planFeatureRepository);
    }

    private Subscription subscription(SubscriptionStatus status, LocalDateTime currentPeriodEnd, String overrides) {
        Plan plan = new Plan();
        ReflectionTestUtils.setField(plan, "id", planId);
        Subscription subscription = new Subscription();
        subscription.setPlan(plan);
        subscription.setStatus(status);
        subscription.setCurrentPeriodEnd(currentPeriodEnd);
        subscription.setCustomOverrides(overrides);
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

    private PlanFeature includedPlanFeature(String featureCode) {
        PlanFeature planFeature = new PlanFeature();
        planFeature.setMode(PlanFeatureMode.INCLUDED);
        planFeature.setFeature(feature(featureCode));
        return planFeature;
    }

    private Feature feature(String featureCode) {
        Feature feature = new Feature();
        feature.setCode(featureCode);
        return feature;
    }
}
