package com.hiveapp.platform.client.plan.service.impl;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;
import com.hiveapp.platform.client.plan.domain.entity.AddOn;
import com.hiveapp.platform.client.plan.domain.entity.AddOnFeature;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeOperation;
import com.hiveapp.platform.client.plan.domain.entity.QuotaPackage;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.AddOnFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.QuotaPackageRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeOperationRepository;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeRequest;
import com.hiveapp.platform.client.plan.dto.QuotaPackageSelection;
import com.hiveapp.platform.client.plan.service.BillingCalculator;
import com.hiveapp.platform.client.plan.service.SubscriptionOverrideReader;
import com.hiveapp.platform.client.plan.service.SubscriptionSnapshotFactory;
import com.hiveapp.platform.client.plan.service.SubscriptionSnapshotReader;
import com.hiveapp.platform.client.plan.service.SubscriptionImpactAnalyzer;
import com.hiveapp.platform.client.plan.service.SubscriptionLifecycleManager;
import com.hiveapp.platform.client.plan.service.SubscriptionPeriodCalculator;
import com.hiveapp.platform.client.plan.service.SubscriptionCheckoutService;
import com.hiveapp.platform.client.plan.service.SubscriptionChangeActivationService;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionFeatureSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionAddOnSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrides;
import com.hiveapp.platform.registry.definition.FeatureDefinitionCollector;
import com.hiveapp.platform.registry.definition.WorkspaceFeature;
import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import com.hiveapp.shared.money.Money;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceImplTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PlanRepository planRepository;
    @Mock private PlanFeatureRepository planFeatureRepository;
    @Mock private AddOnRepository addOnRepository;
    @Mock private AddOnFeatureRepository addOnFeatureRepository;
    @Mock private QuotaPackageRepository quotaPackageRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private BillingCalculator billingCalculator;
    @Mock private SubscriptionOverrideReader subscriptionOverrideReader;
    @Mock private SubscriptionSnapshotFactory subscriptionSnapshotFactory;
    @Mock private SubscriptionSnapshotReader subscriptionSnapshotReader;
    @Mock private ObjectProvider<FeatureDefinitionCollector> featureDefinitionCollectorProvider;
    @Mock private FeatureDefinitionCollector featureDefinitionCollector;
    @Mock private SubscriptionImpactAnalyzer subscriptionImpactAnalyzer;
    @Mock private SubscriptionLifecycleManager subscriptionLifecycleManager;
    @Mock private SubscriptionPeriodCalculator subscriptionPeriodCalculator;
    @Mock private SubscriptionChangeOperationRepository subscriptionChangeOperationRepository;
    @Mock private SubscriptionCheckoutService subscriptionCheckoutService;
    @Mock private SubscriptionChangeActivationService subscriptionChangeActivationService;

    @InjectMocks
    private SubscriptionServiceImpl subscriptionService;

    @Test
    void updateOverridesRejectsInvalidConfigurationBeforePersistence() {
        UUID accountId = UUID.randomUUID();
        Plan plan = plan("PRO", true);
        ReflectionTestUtils.setField(plan, "id", UUID.randomUUID());
        Subscription subscription = subscription(plan, SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findActiveByAccountId(accountId)).thenReturn(Optional.of(subscription));
        when(featureDefinitionCollectorProvider.getObject()).thenReturn(featureDefinitionCollector);
        when(featureDefinitionCollector.collectByCode()).thenReturn(Map.of());
        when(planFeatureRepository.findAllByPlanId(plan.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> subscriptionService.updateOverrides(accountId, Set.of("MISSING_ADDON"), List.of()))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("One or more selected AddOns do not exist.");

        verify(subscriptionOverrideReader, never()).write(org.mockito.ArgumentMatchers.any());
        verify(subscriptionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateOverridesPersistsValidatedQuotaPackageSelection() {
        UUID accountId = UUID.randomUUID();
        Plan plan = plan("PRO", true);
        ReflectionTestUtils.setField(plan, "id", UUID.randomUUID());
        Subscription subscription = subscription(plan, SubscriptionStatus.ACTIVE);
        List<QuotaPackageSelection> quotaPackages = List.of(new QuotaPackageSelection("MEMBERS_10", 1));
        QuotaPackage quotaPackage = quotaPackage("MEMBERS_10", WorkspaceFeature.MEMBERS, plan);
        PlanFeature workspace = planFeature(plan, WorkspaceFeature.CODE, PlanFeatureMode.INCLUDED,
                List.of(new QuotaLimitEntry(WorkspaceFeature.MEMBERS, 3L)));
        var snapshot = new SubscriptionEntitlementSnapshot(
                "PRO", BigDecimal.ZERO, "USD", BillingCycle.MONTHLY,
                List.of(new SubscriptionFeatureSnapshot(
                        WorkspaceFeature.CODE, List.of(new QuotaLimitEntry(WorkspaceFeature.MEMBERS, 3L)))),
                List.of());
        when(subscriptionRepository.findActiveByAccountId(accountId)).thenReturn(Optional.of(subscription));
        when(featureDefinitionCollectorProvider.getObject()).thenReturn(featureDefinitionCollector);
        when(featureDefinitionCollector.collectByCode())
                .thenReturn(Map.of(WorkspaceFeature.CODE, WorkspaceFeature.definition()));
        when(planFeatureRepository.findAllByPlanId(plan.getId())).thenReturn(List.of(workspace));
        when(subscriptionSnapshotFactory.fromPlan(plan, Set.of())).thenReturn(snapshot);
        when(quotaPackageRepository.findAllByCodeIn(Set.of("MEMBERS_10")))
                .thenReturn(List.of(quotaPackage));
        when(subscriptionSnapshotFactory.fromPlan(plan, Set.of(), quotaPackages)).thenReturn(snapshot);
        when(subscriptionOverrideReader.write(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(billingCalculator.calculateMoney(subscription)).thenReturn(Money.of(new BigDecimal("39.99"), "USD"));
        when(subscriptionRepository.save(subscription)).thenReturn(subscription);

        Subscription result = subscriptionService.updateOverrides(accountId, Set.of(), quotaPackages);

        assertThat(result.getCustomOverrides().quotaPackages()).containsExactlyElementsOf(quotaPackages);
        assertThat(result.getCurrentPrice()).isEqualByComparingTo("39.99");
        verify(subscriptionRepository).save(subscription);
    }

    @Test
    void previewRejectsQuotaPackageQuantityAboveConfiguredMaximum() {
        UUID accountId = UUID.randomUUID();
        Plan plan = plan("PRO", true);
        ReflectionTestUtils.setField(plan, "id", UUID.randomUUID());
        PlanFeature workspace = planFeature(plan, WorkspaceFeature.CODE, PlanFeatureMode.INCLUDED,
                List.of(new QuotaLimitEntry(WorkspaceFeature.MEMBERS, 3L)));
        var baseSnapshot = new SubscriptionEntitlementSnapshot(
                "PRO", BigDecimal.ZERO, "USD", BillingCycle.MONTHLY,
                List.of(new SubscriptionFeatureSnapshot(
                        WorkspaceFeature.CODE, List.of(new QuotaLimitEntry(WorkspaceFeature.MEMBERS, 3L)))),
                List.of());
        QuotaPackage item = quotaPackage("MEMBERS_10", WorkspaceFeature.MEMBERS, plan);
        var selection = new QuotaPackageSelection("MEMBERS_10", 2);

        when(subscriptionRepository.findActiveByAccountId(accountId))
                .thenReturn(Optional.of(subscription(plan, SubscriptionStatus.ACTIVE)));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(plan));
        when(featureDefinitionCollectorProvider.getObject()).thenReturn(featureDefinitionCollector);
        when(featureDefinitionCollector.collectByCode())
                .thenReturn(Map.of(WorkspaceFeature.CODE, WorkspaceFeature.definition()));
        when(planFeatureRepository.findAllByPlanId(plan.getId())).thenReturn(List.of(workspace));
        when(subscriptionSnapshotFactory.fromPlan(plan, Set.of())).thenReturn(baseSnapshot);
        when(quotaPackageRepository.findAllByCodeIn(Set.of("MEMBERS_10"))).thenReturn(List.of(item));

        assertThatThrownBy(() -> subscriptionService.previewChange(
                accountId, new SubscriptionChangeRequest("PRO", Set.of(), List.of(selection))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Quota package MEMBERS_10 quantity must be between 1 and 1.");
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
        doAnswer(invocation -> {
            ((Subscription) invocation.getArgument(0)).setStatus(SubscriptionStatus.CANCELLED);
            return null;
        }).when(subscriptionLifecycleManager).closeForReplacement(any(Subscription.class));
        doAnswer(invocation -> {
            Subscription value = invocation.getArgument(0);
            value.setStatus(invocation.getArgument(1));
            SubscriptionPeriodCalculator.Period valuePeriod = invocation.getArgument(2);
            value.setCurrentPeriodStart(valuePeriod.startsAt());
            value.setCurrentPeriodEnd(valuePeriod.endsAt());
            value.setEntitlementSnapshot(value.getEntitlementSnapshot()
                    .withEffectivePeriod(valuePeriod.startsAt(), valuePeriod.endsAt()));
            return null;
        }).when(subscriptionLifecycleManager).initialize(
                any(Subscription.class), any(SubscriptionStatus.class),
                any(SubscriptionPeriodCalculator.Period.class));
        when(subscriptionOverrideReader.write(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var snapshot = SubscriptionEntitlementSnapshot.empty(
                "PRO", BigDecimal.ZERO, "USD", BillingCycle.MONTHLY);
        when(subscriptionSnapshotFactory.fromPlan(pro)).thenReturn(snapshot);
        when(subscriptionSnapshotReader.write(snapshot)).thenReturn(snapshot);
        when(subscriptionPeriodCalculator.recurring(BillingCycle.MONTHLY))
                .thenReturn(period());
        when(subscriptionRepository.saveAndFlush(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Subscription result = subscriptionService.createSubscription(accountId, "PRO");

        assertThat(active.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(trialing.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(result.getPlan()).isEqualTo(pro);
        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(result.getAccount()).isEqualTo(account);
        assertThat(result.getEntitlementSnapshot().planCode()).isEqualTo("PRO");
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
    void previewRejectsUnknownAddOnIdentity() {
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
                new SubscriptionChangeRequest("PRO", Set.of("MISSING_ADDON"), List.of())))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("One or more selected AddOns do not exist.");
    }

    @Test
    void previewReportsQuotaConflictWithCompoundFeatureAndResourceIdentity() {
        UUID accountId = UUID.randomUUID();
        Plan pro = plan("PRO", true);
        ReflectionTestUtils.setField(pro, "id", UUID.randomUUID());
        Plan free = plan("FREE", true);
        Subscription current = subscription(free, SubscriptionStatus.ACTIVE);
        SubscriptionEntitlementSnapshot currentSnapshot = new SubscriptionEntitlementSnapshot(
                "FREE", BigDecimal.ZERO, "USD", BillingCycle.MONTHLY,
                List.of(new SubscriptionFeatureSnapshot(
                        WorkspaceFeature.CODE,
                        List.of(new QuotaLimitEntry(WorkspaceFeature.MEMBERS, 10L)))),
                List.of());
        current.setEntitlementSnapshot(currentSnapshot);
        current.setCurrentMoney(Money.zero("USD"));
        PlanFeature workspace = planFeature(pro, WorkspaceFeature.CODE, null,
                List.of(new QuotaLimitEntry(WorkspaceFeature.MEMBERS, 2L)));
        SubscriptionEntitlementSnapshot targetSnapshot = new SubscriptionEntitlementSnapshot(
                "PRO",
                BigDecimal.ZERO,
                "USD",
                BillingCycle.MONTHLY,
                List.of(new SubscriptionFeatureSnapshot(
                        WorkspaceFeature.CODE,
                        List.of(new QuotaLimitEntry(WorkspaceFeature.MEMBERS, 2L)))),
                List.of());

        when(subscriptionRepository.findActiveByAccountId(accountId)).thenReturn(Optional.of(current));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(pro));
        when(featureDefinitionCollectorProvider.getObject()).thenReturn(featureDefinitionCollector);
        when(featureDefinitionCollector.collectByCode())
                .thenReturn(Map.of(WorkspaceFeature.CODE, WorkspaceFeature.definition()));
        when(planFeatureRepository.findAllByPlanId(pro.getId())).thenReturn(List.of(workspace));
        when(subscriptionSnapshotFactory.fromPlan(pro, Set.of())).thenReturn(targetSnapshot);
        when(subscriptionSnapshotFactory.fromPlan(pro, Set.of(), List.of())).thenReturn(targetSnapshot);
        when(subscriptionImpactAnalyzer.analyze(accountId, current, targetSnapshot))
                .thenReturn(List.of(new com.hiveapp.platform.client.plan.dto.SubscriptionChangeConflict(
                        "QUOTA_BELOW_USAGE", WorkspaceFeature.CODE, WorkspaceFeature.MEMBERS,
                        3L, 2L, "Current usage is above the requested limit.")));
        when(billingCalculator.calculateMoney(any())).thenReturn(Money.zero("USD"));

        var preview = subscriptionService.previewChange(
                accountId,
                new SubscriptionChangeRequest("PRO", Set.of(), List.of()));

        assertThat(preview.immediateAllowed()).isFalse();
        assertThat(preview.conflicts()).hasSize(1);
        assertThat(preview.conflicts().getFirst().code()).isEqualTo("QUOTA_BELOW_USAGE");
    }

    @Test
    void previewSelectsFirstClassAddOnByIdentityAndSnapshotsItsFeature() {
        UUID accountId = UUID.randomUUID();
        Plan free = plan("FREE", true);
        ReflectionTestUtils.setField(free, "id", UUID.randomUUID());
        Subscription current = subscription(free, SubscriptionStatus.ACTIVE);
        current.setCurrentMoney(Money.zero("USD"));
        current.setCustomOverrides(SubscriptionOverrides.empty());
        PlanFeature optional = planFeature(
                free, WorkspaceFeature.CODE, PlanFeatureMode.OPTIONAL_ADD_ON, List.of());
        AddOn addOn = addOn("EXTRA_MEMBERS");
        AddOnFeature addOnFeature = new AddOnFeature();
        addOnFeature.setAddOn(addOn);
        addOnFeature.setFeature(optional.getFeature());
        SubscriptionEntitlementSnapshot snapshot = new SubscriptionEntitlementSnapshot(
                "FREE", BigDecimal.ZERO, "USD", BillingCycle.MONTHLY,
                List.of(new SubscriptionFeatureSnapshot(WorkspaceFeature.CODE, List.of())),
                List.of(new SubscriptionAddOnSnapshot(
                        "EXTRA_MEMBERS", "Extra members", 1, BigDecimal.TEN, "USD",
                        BillingCycle.MONTHLY, List.of(WorkspaceFeature.CODE))));

        when(subscriptionRepository.findActiveByAccountId(accountId)).thenReturn(Optional.of(current));
        when(planRepository.findByCode("FREE")).thenReturn(Optional.of(free));
        when(featureDefinitionCollectorProvider.getObject()).thenReturn(featureDefinitionCollector);
        when(featureDefinitionCollector.collectByCode())
                .thenReturn(Map.of(WorkspaceFeature.CODE, WorkspaceFeature.definition()));
        when(planFeatureRepository.findAllByPlanId(free.getId())).thenReturn(List.of(optional));
        when(planFeatureRepository.findByPlanIdAndFeature_Code(free.getId(), WorkspaceFeature.CODE))
                .thenReturn(Optional.of(optional));
        when(addOnRepository.findAllByCodeIn(Set.of("EXTRA_MEMBERS"))).thenReturn(List.of(addOn));
        when(addOnFeatureRepository.findAllByAddOnId(addOn.getId())).thenReturn(List.of(addOnFeature));
        when(subscriptionSnapshotFactory.fromPlan(free, Set.of("EXTRA_MEMBERS"))).thenReturn(snapshot);
        when(subscriptionSnapshotFactory.fromPlan(free, Set.of("EXTRA_MEMBERS"), List.of())).thenReturn(snapshot);
        when(billingCalculator.calculateMoney(any())).thenReturn(Money.of(BigDecimal.TEN, "USD"));

        var preview = subscriptionService.previewChange(
                accountId, new SubscriptionChangeRequest("FREE", Set.of("EXTRA_MEMBERS"), List.of()));

        assertThat(preview.immediateAllowed()).isTrue();
        assertThat(preview.addOnCodes()).containsExactly("EXTRA_MEMBERS");
        assertThat(preview.effectiveFeatureCodes()).containsExactly(WorkspaceFeature.CODE);
        assertThat(preview.previewPrice()).isEqualByComparingTo("10");
    }

    @Test
    void catalogHidesActiveAddOnWhenItsFeatureIsNoLongerAvailableForNewSales() {
        UUID accountId = UUID.randomUUID();
        Plan plan = plan("FREE", true);
        ReflectionTestUtils.setField(plan, "id", UUID.randomUUID());
        Subscription current = subscription(plan, SubscriptionStatus.ACTIVE);
        current.setCurrentMoney(Money.zero("USD"));
        PlanFeature optional = planFeature(
                plan, WorkspaceFeature.CODE, PlanFeatureMode.OPTIONAL_ADD_ON, List.of());
        optional.getFeature().setStatus(FeatureStatus.INTERNAL);
        AddOn addOn = addOn("EXTRA_MEMBERS");
        AddOnFeature addOnFeature = new AddOnFeature();
        addOnFeature.setAddOn(addOn);
        addOnFeature.setFeature(optional.getFeature());

        when(subscriptionRepository.findActiveByAccountId(accountId)).thenReturn(Optional.of(current));
        when(subscriptionOverrideReader.read(current.getCustomOverrides()))
                .thenReturn(SubscriptionOverrides.empty());
        when(featureDefinitionCollectorProvider.getObject()).thenReturn(featureDefinitionCollector);
        when(featureDefinitionCollector.collectByCode())
                .thenReturn(Map.of(WorkspaceFeature.CODE, WorkspaceFeature.definition()));
        when(planRepository.findAll()).thenReturn(List.of(plan));
        when(planFeatureRepository.findAllByPlanId(plan.getId())).thenReturn(List.of(optional));
        when(addOnRepository.findAll()).thenReturn(List.of(addOn));
        when(addOnFeatureRepository.findAllByAddOnId(addOn.getId())).thenReturn(List.of(addOnFeature));

        var catalog = subscriptionService.catalog(accountId);

        assertThat(catalog.plans()).singleElement().satisfies(catalogPlan -> {
            assertThat(catalogPlan.features()).isEmpty();
            assertThat(catalogPlan.addOns()).isEmpty();
        });
    }

    @Test
    void paidApplyCreatesAwaitingConfirmationOperationWithoutChangingEntitlement() {
        UUID accountId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", accountId);
        Plan free = plan("FREE", true);
        Plan pro = plan("PRO", true);
        ReflectionTestUtils.setField(pro, "id", UUID.randomUUID());
        Subscription current = subscription(free, SubscriptionStatus.ACTIVE);
        current.setAccount(account);
        current.setEntitlementSnapshot(SubscriptionEntitlementSnapshot.empty(
                "FREE", BigDecimal.ZERO, "USD", BillingCycle.MONTHLY));
        current.setCustomOverrides(SubscriptionOverrides.empty());
        current.setCurrentMoney(Money.zero("USD"));
        PlanFeature workspace = planFeature(pro, WorkspaceFeature.CODE, null,
                List.of(new QuotaLimitEntry(WorkspaceFeature.MEMBERS, 10L)));
        SubscriptionEntitlementSnapshot targetSnapshot = new SubscriptionEntitlementSnapshot(
                "PRO",
                BigDecimal.valueOf(29),
                "USD",
                BillingCycle.MONTHLY,
                List.of(new SubscriptionFeatureSnapshot(
                        WorkspaceFeature.CODE,
                        List.of(new QuotaLimitEntry(WorkspaceFeature.MEMBERS, 10L)))),
                List.of());

        when(accountRepository.findByIdForSubscriptionUpdate(accountId)).thenReturn(Optional.of(account));
        when(subscriptionRepository.findActiveByAccountId(accountId)).thenReturn(Optional.of(current));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(pro));
        when(featureDefinitionCollectorProvider.getObject()).thenReturn(featureDefinitionCollector);
        when(featureDefinitionCollector.collectByCode())
                .thenReturn(Map.of(WorkspaceFeature.CODE, WorkspaceFeature.definition()));
        when(planFeatureRepository.findAllByPlanId(pro.getId())).thenReturn(List.of(workspace));
        when(subscriptionSnapshotFactory.fromPlan(pro, Set.of())).thenReturn(targetSnapshot);
        when(subscriptionSnapshotFactory.fromPlan(pro, Set.of(), List.of())).thenReturn(targetSnapshot);
        when(subscriptionSnapshotReader.read(current.getEntitlementSnapshot()))
                .thenReturn(Optional.of(SubscriptionEntitlementSnapshot.empty(
                        "FREE", BigDecimal.ZERO, "USD", BillingCycle.MONTHLY)));
        when(subscriptionOverrideReader.read(current.getCustomOverrides()))
                .thenReturn(SubscriptionOverrides.empty());
        when(billingCalculator.calculateMoney(any())).thenReturn(Money.of(BigDecimal.valueOf(29), "USD"));
        when(subscriptionPeriodCalculator.recurring(BillingCycle.MONTHLY)).thenReturn(period());
        when(subscriptionChangeOperationRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> {
                    var operation = invocation.getArgument(0, SubscriptionChangeOperation.class);
                    ReflectionTestUtils.setField(operation, "id", UUID.randomUUID());
                    return operation;
                });

        var response = subscriptionService.applyChange(
                accountId,
                actorUserId,
                new SubscriptionChangeRequest("PRO", Set.of(), List.of()));

        assertThat(current.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(response.subscription().plan().code()).isEqualTo("FREE");
        assertThat(response.operation().status()).isEqualTo(SubscriptionChangeStatus.AWAITING_CONFIRMATION);
        verify(subscriptionCheckoutService).initiate(
                any(), org.mockito.ArgumentMatchers.eq(Money.of(BigDecimal.valueOf(29), "USD")),
                org.mockito.ArgumentMatchers.eq(actorUserId));
        verify(subscriptionChangeActivationService, never()).activate(any(), any());
    }

    private Plan plan(String code, boolean active) {
        Plan plan = new Plan();
        plan.setCode(code);
        plan.setName(code);
        plan.setStatus(active ? PlanStatus.ACTIVE : PlanStatus.INACTIVE);
        plan.setMoney(Money.zero("USD"));
        plan.setBillingCycle(BillingCycle.MONTHLY);
        return plan;
    }

    private Subscription subscription(Plan plan, SubscriptionStatus status) {
        Subscription subscription = new Subscription();
        subscription.setPlan(plan);
        subscription.setStatus(status);
        subscription.setCustomOverrides(SubscriptionOverrides.empty());
        subscription.setEntitlementSnapshot(SubscriptionEntitlementSnapshot.empty(
                plan.getCode(), plan.getPrice(), plan.getCurrencyCode(), plan.getBillingCycle()));
        return subscription;
    }

    private SubscriptionPeriodCalculator.Period period() {
        java.time.Instant start = java.time.Instant.parse("2026-08-10T00:00:00Z");
        return new SubscriptionPeriodCalculator.Period(start, start.plusSeconds(2_592_000));
    }

    private PlanFeature planFeature(
            Plan plan, String featureCode, PlanFeatureMode mode, List<QuotaLimitEntry> quotas) {
        Feature feature = new Feature();
        feature.setCode(featureCode);
        feature.setStatus(FeatureStatus.PUBLIC);
        feature.setNewSalesEnabled(true);
        PlanFeature planFeature = new PlanFeature();
        planFeature.setPlan(plan);
        planFeature.setFeature(feature);
        planFeature.setMode(mode != null ? mode : PlanFeatureMode.INCLUDED);
        planFeature.setQuotaConfigs(quotas);
        return planFeature;
    }

    private AddOn addOn(String code) {
        AddOn addOn = new AddOn();
        ReflectionTestUtils.setField(addOn, "id", UUID.randomUUID());
        addOn.setCode(code);
        addOn.setName("Extra members");
        addOn.setMoney(Money.of(BigDecimal.TEN, "USD"));
        addOn.setBillingCycle(BillingCycle.MONTHLY);
        addOn.setStatus(AddOnStatus.ACTIVE);
        return addOn;
    }

    private QuotaPackage quotaPackage(String code, String resource, Plan plan) {
        QuotaPackage item = new QuotaPackage();
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
        item.setCode(code);
        item.setName(code);
        item.setFeature(planFeature(plan, WorkspaceFeature.CODE, PlanFeatureMode.INCLUDED, List.of()).getFeature());
        item.setResource(resource);
        item.setCapacityPerUnit(10);
        item.setMoney(Money.of(BigDecimal.TEN, "USD"));
        item.setBillingCycle(BillingCycle.MONTHLY);
        item.setRepeatable(false);
        item.setMaximumQuantity(1);
        item.setAllowedPlanCodes(Set.of(plan.getCode()));
        item.setStatus(QuotaPackageStatus.ACTIVE);
        return item;
    }
}
