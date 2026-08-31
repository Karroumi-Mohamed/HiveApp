package com.hiveapp.shared.quota;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionFeatureSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionQuotaPackageSnapshot;
import com.hiveapp.platform.client.plan.service.SubscriptionSnapshotReader;
import com.hiveapp.platform.registry.definition.CompanyFeature;
import com.hiveapp.platform.registry.definition.StaffFeature;
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

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionSnapshotReader subscriptionSnapshotReader;

    private QuotaEnforcer quotaEnforcer;
    private UUID accountId;
    private UUID planId;

    @BeforeEach
    void setUp() {
        quotaEnforcer = new QuotaEnforcer(subscriptionRepository, subscriptionSnapshotReader);
        accountId = UUID.randomUUID();
        planId = UUID.randomUUID();
    }

    @Test
    void deniesWhenCurrentUsageHasReachedPlanLimit() {
        when(subscriptionRepository.findByAccountIdAndStatus(accountId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(subscription(new QuotaLimitEntry(StaffFeature.MEMBERS, 3L))));
        when(subscriptionSnapshotReader.read(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));

        assertThatThrownBy(() -> quotaEnforcer.check(
                StaffFeature.definition(), StaffFeature.MEMBERS, accountId, () -> 3L))
                .isInstanceOf(QuotaExceededException.class)
                .hasMessageContaining("limit is 3 persons")
                .hasMessageContaining("current usage is 3");
    }

    @Test
    void unlimitedPlanQuotaSkipsUsageEvaluation() {
        AtomicBoolean evaluated = new AtomicBoolean();
        when(subscriptionRepository.findByAccountIdAndStatus(accountId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(subscription(new QuotaLimitEntry(CompanyFeature.COMPANIES, null))));
        when(subscriptionSnapshotReader.read(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));

        quotaEnforcer.check(CompanyFeature.definition(), CompanyFeature.COMPANIES, accountId, () -> {
            evaluated.set(true);
            return 500L;
        });

        org.assertj.core.api.Assertions.assertThat(evaluated.get()).isFalse();
    }

    @Test
    void snapshotQuotaIsUsedWithoutLivePlanFeature() {
        Subscription subscription = subscription(new QuotaLimitEntry(StaffFeature.MEMBERS, 3L));
        when(subscriptionRepository.findByAccountIdAndStatus(accountId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(subscription));
        when(subscriptionSnapshotReader.read(subscription.getEntitlementSnapshot()))
                .thenReturn(Optional.of(new SubscriptionEntitlementSnapshot(
                        "FREE",
                        java.math.BigDecimal.ZERO,
                        "USD",
                        com.hiveapp.platform.client.plan.domain.constant.BillingCycle.MONTHLY,
                        List.of(new SubscriptionFeatureSnapshot(
                                StaffFeature.CODE,
                                List.of(new QuotaLimitEntry(StaffFeature.MEMBERS, 3L)))),
                        List.of(),
                        List.of())));

        assertThatThrownBy(() -> quotaEnforcer.check(
                StaffFeature.definition(), StaffFeature.MEMBERS, accountId, () -> 3L))
                .isInstanceOf(QuotaExceededException.class);

    }

    @Test
    void snapshottedQuotaPackageRaisesTheFeatureQualifiedLimit() {
        Subscription subscription = subscription(new QuotaLimitEntry(StaffFeature.MEMBERS, 3L));
        when(subscriptionRepository.findByAccountIdAndStatus(accountId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(subscription));
        when(subscriptionSnapshotReader.read(subscription.getEntitlementSnapshot()))
                .thenReturn(Optional.of(new SubscriptionEntitlementSnapshot(
                        "FREE", java.math.BigDecimal.ZERO, "USD",
                        com.hiveapp.platform.client.plan.domain.constant.BillingCycle.MONTHLY,
                        List.of(new SubscriptionFeatureSnapshot(
                                StaffFeature.CODE,
                                List.of(new QuotaLimitEntry(StaffFeature.MEMBERS, 3L)))),
                        List.of(),
                        List.of(new SubscriptionQuotaPackageSnapshot(
                                "MEMBERS_2", "Two members", 1, StaffFeature.CODE,
                                StaffFeature.MEMBERS, 2, 1, java.math.BigDecimal.ONE,
                                "USD", com.hiveapp.platform.client.plan.domain.constant.BillingCycle.MONTHLY)))));

        quotaEnforcer.check(StaffFeature.definition(), StaffFeature.MEMBERS, accountId, () -> 4L);

    }

    @Test
    void missingActiveOrTrialingSubscriptionCannotEvaluateQuota() {
        when(subscriptionRepository.findByAccountIdAndStatus(accountId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.findByAccountIdAndStatus(accountId, SubscriptionStatus.TRIALING))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> quotaEnforcer.check(
                StaffFeature.definition(), StaffFeature.MEMBERS, accountId, () -> 0L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Subscription");
    }

    @Test
    void missingSnapshotFailsClosedInsteadOfTreatingQuotaAsUnlimited() {
        Subscription subscription = subscription(new QuotaLimitEntry(StaffFeature.MEMBERS, 3L));
        when(subscriptionRepository.findByAccountIdAndStatus(accountId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(subscription));
        when(subscriptionSnapshotReader.read(subscription.getEntitlementSnapshot())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> quotaEnforcer.check(
                StaffFeature.definition(), StaffFeature.MEMBERS, accountId, () -> 0L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("snapshot is required");
    }

    private Subscription subscription(QuotaLimitEntry limit) {
        String featureCode = CompanyFeature.COMPANIES.equals(limit.resource())
                ? CompanyFeature.CODE
                : StaffFeature.CODE;
        Plan plan = new Plan();
        ReflectionTestUtils.setField(plan, "id", planId);
        Subscription subscription = new Subscription();
        subscription.setPlan(plan);
        subscription.setCustomOverrides(com.hiveapp.platform.client.plan.dto.SubscriptionOverrides.empty());
        subscription.setEntitlementSnapshot(new SubscriptionEntitlementSnapshot(
                "FREE", java.math.BigDecimal.ZERO, "USD",
                com.hiveapp.platform.client.plan.domain.constant.BillingCycle.MONTHLY,
                limit == null ? List.of() : List.of(new SubscriptionFeatureSnapshot(
                        featureCode, List.of(limit))),
                List.of(), List.of()));
        return subscription;
    }
}
