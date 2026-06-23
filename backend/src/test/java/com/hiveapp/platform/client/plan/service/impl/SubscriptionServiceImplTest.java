package com.hiveapp.platform.client.plan.service.impl;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeRequest;
import com.hiveapp.platform.client.plan.service.BillingCalculator;
import com.hiveapp.platform.client.plan.service.BillingConfigurationValidator;
import com.hiveapp.platform.client.plan.service.SubscriptionOverrideReader;
import com.hiveapp.platform.client.plan.service.SubscriptionSnapshotFactory;
import com.hiveapp.platform.client.plan.service.SubscriptionSnapshotReader;
import com.hiveapp.platform.client.plan.service.SubscriptionUsageService;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionFeatureSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrides;
import com.hiveapp.platform.registry.definition.FeatureDefinitionCollector;
import com.hiveapp.platform.registry.definition.WorkspaceFeature;
import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import com.hiveapp.shared.quota.QuotaOverride;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceImplTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PlanRepository planRepository;
    @Mock private PlanFeatureRepository planFeatureRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private BillingCalculator billingCalculator;
    @Mock private SubscriptionOverrideReader subscriptionOverrideReader;
    @Mock private SubscriptionSnapshotFactory subscriptionSnapshotFactory;
    @Mock private SubscriptionSnapshotReader subscriptionSnapshotReader;
    @Mock private BillingConfigurationValidator billingConfigurationValidator;
    @Mock private ObjectProvider<FeatureDefinitionCollector> featureDefinitionCollectorProvider;
    @Mock private FeatureDefinitionCollector featureDefinitionCollector;
    @Mock private SubscriptionUsageService subscriptionUsageService;

    @InjectMocks
    private SubscriptionServiceImpl subscriptionService;

    @Test
    void updateOverridesRejectsInvalidConfigurationBeforePersistence() {
        UUID accountId = UUID.randomUUID();
        Subscription subscription = new Subscription();
        when(subscriptionRepository.findActiveByAccountId(accountId)).thenReturn(Optional.of(subscription));
        doThrow(new InvalidRequestException("Invalid feature"))
                .when(billingConfigurationValidator).validateSubscriptionOverrides(Set.of("platform.plans"), List.of());

        assertThatThrownBy(() -> subscriptionService.updateOverrides(accountId, Set.of("platform.plans"), List.of()))
                .isInstanceOf(InvalidRequestException.class);

        verify(subscriptionOverrideReader, never()).write(org.mockito.ArgumentMatchers.any());
        verify(subscriptionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateOverridesPersistsValidatedWorkspaceQuotaIncrease() {
        UUID accountId = UUID.randomUUID();
        Subscription subscription = new Subscription();
        List<QuotaOverride> quotaOverrides = List.of(new QuotaOverride("platform.workspace", "members", 10L));
        when(subscriptionRepository.findActiveByAccountId(accountId)).thenReturn(Optional.of(subscription));
        when(subscriptionOverrideReader.write(org.mockito.ArgumentMatchers.any())).thenReturn("{\"quotaOverrides\":[]}");
        when(billingCalculator.calculate(subscription)).thenReturn(new BigDecimal("39.99"));
        when(subscriptionRepository.save(subscription)).thenReturn(subscription);

        Subscription result = subscriptionService.updateOverrides(accountId, Set.of(), quotaOverrides);

        verify(billingConfigurationValidator).validateSubscriptionOverrides(Set.of(), quotaOverrides);
        assertThat(result.getCustomOverrides()).isEqualTo("{\"quotaOverrides\":[]}");
        assertThat(result.getCurrentPrice()).isEqualByComparingTo("39.99");
        verify(subscriptionRepository).save(subscription);
    }

    @Test
    void planAssignmentCancelsExistingUsableSubscriptionsBeforeCreatingReplacement() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account();
        Plan free = plan("FREE", true);
        Plan pro = plan("PRO", true);
        Subscription active = subscription(free, SubscriptionStatus.ACTIVE);
        Subscription trialing = subscription(free, SubscriptionStatus.TRIALING);

        when(accountRepository.findByIdForSubscriptionUpdate(accountId)).thenReturn(Optional.of(account));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(pro));
        when(subscriptionRepository.findAllByAccountIdAndStatusIn(
                accountId, List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING)))
                .thenReturn(List.of(active, trialing));
        when(subscriptionOverrideReader.write(org.mockito.ArgumentMatchers.any()))
                .thenReturn("{\"addedFeatures\":[],\"quotaOverrides\":[]}");
        var snapshot = SubscriptionEntitlementSnapshot.empty("PRO", BigDecimal.ZERO);
        when(subscriptionSnapshotFactory.fromPlan(pro)).thenReturn(snapshot);
        when(subscriptionSnapshotReader.write(snapshot)).thenReturn("{\"planCode\":\"PRO\"}");
        when(subscriptionRepository.saveAndFlush(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Subscription result = subscriptionService.createSubscription(accountId, "PRO");

        assertThat(active.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(trialing.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(result.getPlan()).isEqualTo(pro);
        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(result.getAccount()).isEqualTo(account);
        assertThat(result.getEntitlementSnapshot()).isEqualTo("{\"planCode\":\"PRO\"}");
        verify(subscriptionRepository).saveAllAndFlush(List.of(active, trialing));
    }

    @Test
    void planAssignmentRejectsInactivePlanBeforeChangingCurrentSubscription() {
        UUID accountId = UUID.randomUUID();
        when(accountRepository.findByIdForSubscriptionUpdate(accountId)).thenReturn(Optional.of(new Account()));
        when(planRepository.findByCode("ARCHIVED")).thenReturn(Optional.of(plan("ARCHIVED", false)));

        assertThatThrownBy(() -> subscriptionService.createSubscription(accountId, "ARCHIVED"))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("Inactive plans");

        verify(subscriptionRepository, never()).findAllByAccountIdAndStatusIn(
                any(), org.mockito.ArgumentMatchers.anyCollection());
        verify(subscriptionRepository, never()).saveAndFlush(any(Subscription.class));
    }

    @Test
    void planAssignmentRejectsReassigningTheCurrentActivePlan() {
        UUID accountId = UUID.randomUUID();
        Plan pro = plan("PRO", true);
        when(accountRepository.findByIdForSubscriptionUpdate(accountId)).thenReturn(Optional.of(new Account()));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(pro));
        when(subscriptionRepository.findAllByAccountIdAndStatusIn(
                accountId, List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING)))
                .thenReturn(List.of(subscription(pro, SubscriptionStatus.ACTIVE)));

        assertThatThrownBy(() -> subscriptionService.createSubscription(accountId, "PRO"))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("already subscribed");

        verify(subscriptionRepository, never()).saveAllAndFlush(org.mockito.ArgumentMatchers.anyCollection());
        verify(subscriptionRepository, never()).saveAndFlush(any(Subscription.class));
    }

    @Test
    void previewRejectsSelectingFeatureAlreadyIncludedInTargetPlanAsAddon() {
        UUID accountId = UUID.randomUUID();
        Plan pro = plan("PRO", true);
        ReflectionTestUtils.setField(pro, "id", UUID.randomUUID());
        PlanFeature includedWorkspace = planFeature(pro, WorkspaceFeature.CODE, null,
                List.of(new QuotaLimitEntry(WorkspaceFeature.MEMBERS, 10L)));

        when(subscriptionRepository.findActiveByAccountId(accountId))
                .thenReturn(Optional.of(subscription(plan("FREE", true), SubscriptionStatus.ACTIVE)));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(pro));
        when(featureDefinitionCollectorProvider.getObject()).thenReturn(featureDefinitionCollector);
        when(featureDefinitionCollector.collectByCode())
                .thenReturn(Map.of(WorkspaceFeature.CODE, WorkspaceFeature.definition()));
        when(planFeatureRepository.findAllByPlanId(pro.getId())).thenReturn(List.of(includedWorkspace));

        assertThatThrownBy(() -> subscriptionService.previewChange(
                accountId,
                new SubscriptionChangeRequest("PRO", Set.of(WorkspaceFeature.CODE), List.of())))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Feature platform.workspace is already included in plan PRO.");
    }

    @Test
    void previewReportsQuotaConflictWhenRequestedLimitIsBelowCurrentUsage() {
        UUID accountId = UUID.randomUUID();
        Plan pro = plan("PRO", true);
        ReflectionTestUtils.setField(pro, "id", UUID.randomUUID());
        Plan free = plan("FREE", true);
        Subscription current = subscription(free, SubscriptionStatus.ACTIVE);
        current.setEntitlementSnapshot("{\"planCode\":\"FREE\"}");
        current.setCurrentPrice(BigDecimal.ZERO);
        PlanFeature workspace = planFeature(pro, WorkspaceFeature.CODE, null,
                List.of(new QuotaLimitEntry(WorkspaceFeature.MEMBERS, 10L)));
        SubscriptionEntitlementSnapshot targetSnapshot = new SubscriptionEntitlementSnapshot(
                "PRO",
                BigDecimal.ZERO,
                List.of(new SubscriptionFeatureSnapshot(
                        WorkspaceFeature.CODE,
                        null,
                        List.of(new QuotaLimitEntry(WorkspaceFeature.MEMBERS, 10L)))));

        when(subscriptionRepository.findActiveByAccountId(accountId)).thenReturn(Optional.of(current));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(pro));
        when(featureDefinitionCollectorProvider.getObject()).thenReturn(featureDefinitionCollector);
        when(featureDefinitionCollector.collectByCode())
                .thenReturn(Map.of(WorkspaceFeature.CODE, WorkspaceFeature.definition()));
        when(planFeatureRepository.findAllByPlanId(pro.getId())).thenReturn(List.of(workspace));
        when(subscriptionSnapshotFactory.fromPlan(pro, Set.of())).thenReturn(targetSnapshot);
        when(subscriptionSnapshotReader.read(current.getEntitlementSnapshot()))
                .thenReturn(Optional.of(targetSnapshot));
        when(subscriptionSnapshotReader.write(targetSnapshot)).thenReturn("{\"planCode\":\"PRO\"}");
        when(subscriptionOverrideReader.write(any())).thenReturn("{\"quotaOverrides\":[]}");
        when(subscriptionUsageService.currentUsage(accountId, WorkspaceFeature.CODE, WorkspaceFeature.MEMBERS))
                .thenReturn(3L);
        when(billingCalculator.calculate(any())).thenReturn(BigDecimal.ZERO);

        var preview = subscriptionService.previewChange(
                accountId,
                new SubscriptionChangeRequest(
                        "PRO",
                        Set.of(),
                        List.of(new QuotaOverride(WorkspaceFeature.CODE, WorkspaceFeature.MEMBERS, 2L))));

        assertThat(preview.immediateAllowed()).isFalse();
        assertThat(preview.conflicts()).hasSize(1);
        assertThat(preview.conflicts().getFirst().code()).isEqualTo("QUOTA_BELOW_USAGE");
    }

    @Test
    void applyChangeCancelsCurrentUsableSubscriptionsAndStoresSnapshotWithOverrides() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", accountId);
        Plan free = plan("FREE", true);
        Plan pro = plan("PRO", true);
        ReflectionTestUtils.setField(pro, "id", UUID.randomUUID());
        Subscription current = subscription(free, SubscriptionStatus.ACTIVE);
        current.setAccount(account);
        current.setEntitlementSnapshot("{\"planCode\":\"FREE\"}");
        current.setCustomOverrides("{\"addedFeatures\":[],\"quotaOverrides\":[]}");
        current.setCurrentPrice(BigDecimal.ZERO);
        PlanFeature workspace = planFeature(pro, WorkspaceFeature.CODE, null,
                List.of(new QuotaLimitEntry(WorkspaceFeature.MEMBERS, 10L)));
        SubscriptionEntitlementSnapshot targetSnapshot = new SubscriptionEntitlementSnapshot(
                "PRO",
                BigDecimal.valueOf(29),
                List.of(new SubscriptionFeatureSnapshot(
                        WorkspaceFeature.CODE,
                        null,
                        List.of(new QuotaLimitEntry(WorkspaceFeature.MEMBERS, 10L)))));

        when(accountRepository.findByIdForSubscriptionUpdate(accountId)).thenReturn(Optional.of(account));
        when(subscriptionRepository.findActiveByAccountId(accountId)).thenReturn(Optional.of(current));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(pro));
        when(featureDefinitionCollectorProvider.getObject()).thenReturn(featureDefinitionCollector);
        when(featureDefinitionCollector.collectByCode())
                .thenReturn(Map.of(WorkspaceFeature.CODE, WorkspaceFeature.definition()));
        when(planFeatureRepository.findAllByPlanId(pro.getId())).thenReturn(List.of(workspace));
        when(subscriptionSnapshotFactory.fromPlan(pro, Set.of())).thenReturn(targetSnapshot);
        when(subscriptionSnapshotReader.read(current.getEntitlementSnapshot()))
                .thenReturn(Optional.of(SubscriptionEntitlementSnapshot.empty("FREE", BigDecimal.ZERO)));
        when(subscriptionSnapshotReader.write(targetSnapshot)).thenReturn("{\"planCode\":\"PRO\"}");
        when(subscriptionOverrideReader.read(current.getCustomOverrides()))
                .thenReturn(SubscriptionOverrides.empty());
        when(subscriptionOverrideReader.write(any())).thenReturn("{\"addedFeatures\":[],\"quotaOverrides\":[]}");
        when(billingCalculator.calculate(any())).thenReturn(BigDecimal.valueOf(29));
        when(subscriptionRepository.findAllByAccountIdAndStatusIn(
                accountId, List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING)))
                .thenReturn(List.of(current));
        when(subscriptionRepository.saveAndFlush(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = subscriptionService.applyChange(
                accountId,
                new SubscriptionChangeRequest("PRO", Set.of(), List.of()));

        assertThat(current.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(response.subscription().plan().code()).isEqualTo("PRO");
        assertThat(response.subscription().currentPrice()).isEqualByComparingTo("29");
        verify(subscriptionRepository).saveAllAndFlush(List.of(current));
    }

    private Plan plan(String code, boolean active) {
        Plan plan = new Plan();
        plan.setCode(code);
        plan.setName(code);
        plan.setActive(active);
        plan.setPrice(BigDecimal.ZERO);
        return plan;
    }

    private Subscription subscription(Plan plan, SubscriptionStatus status) {
        Subscription subscription = new Subscription();
        subscription.setPlan(plan);
        subscription.setStatus(status);
        return subscription;
    }

    private PlanFeature planFeature(Plan plan, String featureCode, BigDecimal addOnPrice, List<QuotaLimitEntry> quotas) {
        Feature feature = new Feature();
        feature.setCode(featureCode);
        feature.setStatus(FeatureStatus.PUBLIC);
        feature.setActive(true);
        PlanFeature planFeature = new PlanFeature();
        planFeature.setPlan(plan);
        planFeature.setFeature(feature);
        planFeature.setAddOnPrice(addOnPrice);
        planFeature.setQuotaConfigs(quotas);
        return planFeature;
    }
}
