package com.hiveapp.shared.quota;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionFeatureSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrides;
import com.hiveapp.platform.client.plan.service.SubscriptionOverrideReader;
import com.hiveapp.platform.client.plan.service.SubscriptionSnapshotReader;
import com.hiveapp.platform.registry.definition.WorkspaceFeature;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuotaEnforcerTest {

    @Mock private PlanFeatureRepository planFeatureRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionOverrideReader subscriptionOverrideReader;
    @Mock private SubscriptionSnapshotReader subscriptionSnapshotReader;

    private QuotaEnforcer quotaEnforcer;
    private UUID accountId;
    private UUID planId;

    @BeforeEach
    void setUp() {
        quotaEnforcer = new QuotaEnforcer(
                planFeatureRepository,
                subscriptionRepository,
                subscriptionOverrideReader,
                subscriptionSnapshotReader);
        accountId = UUID.randomUUID();
        planId = UUID.randomUUID();
    }

    @Test
    void deniesWhenCurrentUsageHasReachedPlanLimit() {
        when(subscriptionRepository.findByAccountIdAndStatus(accountId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(subscription(null)));
        when(subscriptionSnapshotReader.read(null)).thenReturn(Optional.empty());
        when(planFeatureRepository.findByPlanIdAndFeature_Code(planId, WorkspaceFeature.CODE))
                .thenReturn(Optional.of(planFeature(new QuotaLimitEntry(WorkspaceFeature.MEMBERS, 3L))));

        assertThatThrownBy(() -> quotaEnforcer.check(
                WorkspaceFeature.definition(), WorkspaceFeature.MEMBERS, accountId, () -> 3L))
                .isInstanceOf(QuotaExceededException.class)
                .hasMessageContaining("limit is 3 persons")
                .hasMessageContaining("current usage is 3");
    }

    @Test
    void unlimitedPlanQuotaSkipsUsageEvaluation() {
        AtomicBoolean evaluated = new AtomicBoolean();
        when(subscriptionRepository.findByAccountIdAndStatus(accountId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(subscription(null)));
        when(subscriptionSnapshotReader.read(null)).thenReturn(Optional.empty());
        when(planFeatureRepository.findByPlanIdAndFeature_Code(planId, WorkspaceFeature.CODE))
                .thenReturn(Optional.of(planFeature(new QuotaLimitEntry(WorkspaceFeature.COMPANIES, null))));

        quotaEnforcer.check(WorkspaceFeature.definition(), WorkspaceFeature.COMPANIES, accountId, () -> {
            evaluated.set(true);
            return 500L;
        });

        org.assertj.core.api.Assertions.assertThat(evaluated.get()).isFalse();
    }

    @Test
    void snapshotQuotaIsUsedWithoutLivePlanFeature() {
        Subscription subscription = subscription(null);
        subscription.setEntitlementSnapshot("{\"snapshot\":true}");
        when(subscriptionRepository.findByAccountIdAndStatus(accountId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(subscription));
        when(subscriptionSnapshotReader.read(subscription.getEntitlementSnapshot()))
                .thenReturn(Optional.of(new SubscriptionEntitlementSnapshot(
                        "FREE",
                        java.math.BigDecimal.ZERO,
                        List.of(new SubscriptionFeatureSnapshot(
                                WorkspaceFeature.CODE,
                                null,
                                List.of(new QuotaLimitEntry(WorkspaceFeature.MEMBERS, 3L)))))));

        assertThatThrownBy(() -> quotaEnforcer.check(
                WorkspaceFeature.definition(), WorkspaceFeature.MEMBERS, accountId, () -> 3L))
                .isInstanceOf(QuotaExceededException.class);

        verifyNoInteractions(planFeatureRepository);
    }

    @Test
    void subscriptionOverrideTakesPrecedenceOverPlanDefault() {
        Subscription subscription = subscription("{\"quotaOverrides\":[]}");
        when(subscriptionRepository.findByAccountIdAndStatus(accountId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(subscription));
        when(subscriptionOverrideReader.read(subscription.getCustomOverrides()))
                .thenReturn(new SubscriptionOverrides(
                        java.util.Set.of(),
                        List.of(new QuotaOverride(WorkspaceFeature.CODE, WorkspaceFeature.MEMBERS, 5L))));

        quotaEnforcer.check(WorkspaceFeature.definition(), WorkspaceFeature.MEMBERS, accountId, () -> 3L);

        verifyNoInteractions(planFeatureRepository);
    }

    @Test
    void missingActiveOrTrialingSubscriptionCannotEvaluateQuota() {
        when(subscriptionRepository.findByAccountIdAndStatus(accountId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.findByAccountIdAndStatus(accountId, SubscriptionStatus.TRIALING))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> quotaEnforcer.check(
                WorkspaceFeature.definition(), WorkspaceFeature.MEMBERS, accountId, () -> 0L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Subscription");
    }

    private Subscription subscription(String customOverrides) {
        Plan plan = new Plan();
        ReflectionTestUtils.setField(plan, "id", planId);
        Subscription subscription = new Subscription();
        subscription.setPlan(plan);
        subscription.setCustomOverrides(customOverrides);
        return subscription;
    }

    private PlanFeature planFeature(QuotaLimitEntry limit) {
        PlanFeature planFeature = new PlanFeature();
        planFeature.setQuotaConfigs(List.of(limit));
        return planFeature;
    }
}
