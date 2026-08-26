package com.hiveapp.platform.client.plan.service.impl;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;
import com.hiveapp.platform.client.plan.domain.constant.ExtensionAvailabilityReason;
import com.hiveapp.platform.client.plan.domain.constant.ExtensionResolutionSource;
import com.hiveapp.platform.client.plan.domain.entity.AddOn;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeOperation;
import com.hiveapp.platform.client.plan.domain.entity.ProductPrice;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeOperationRepository;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeRequest;
import com.hiveapp.platform.client.plan.dto.QuotaPackageSelection;
import com.hiveapp.platform.client.plan.service.BillingCalculator;
import com.hiveapp.platform.client.plan.service.CommercialCatalogResolver;
import com.hiveapp.platform.client.plan.service.CommercialSelectionFinalizer;
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
import com.hiveapp.platform.registry.definition.StaffFeature;
import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.exception.OperationBlockedException;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import com.hiveapp.shared.money.Money;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceImplTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PlanRepository planRepository;
    @Mock private PlanFeatureRepository planFeatureRepository;
    @Mock private AddOnRepository addOnRepository;
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
    @Mock private com.hiveapp.platform.client.plan.service.ProductPriceResolver productPriceResolver;
    @Mock private CommercialCatalogResolver commercialCatalogResolver;
    @Mock private CommercialSelectionFinalizer commercialSelectionFinalizer;

    @InjectMocks
    private SubscriptionServiceImpl subscriptionService;

    @BeforeEach
    void defaultPriceResolution() {
        lenient().when(productPriceResolver.resolvePlan(
                        any(Plan.class), nullable(com.hiveapp.platform.client.plan.dto.ProductPriceSelectionRequest.class)))
                .thenAnswer(invocation -> {
                    Plan plan = invocation.getArgument(0);
                    ProductPrice price = ProductPrice.draft(
                            plan, plan.money(), plan.getBillingCycle(), java.time.Instant.EPOCH, null);
                    price.activate();
                    ReflectionTestUtils.setField(price, "id", UUID.randomUUID());
                    return price;
                });
        lenient().when(subscriptionOverrideReader.read(any()))
                .thenAnswer(invocation -> invocation.getArgument(0) instanceof SubscriptionOverrides overrides
                        ? overrides : SubscriptionOverrides.empty());
        lenient().when(commercialCatalogResolver.resolveCatalog(any()))
                .thenReturn(new CommercialCatalogResolver.CatalogResolution(List.of()));
        lenient().when(commercialCatalogResolver.resolveSelection(
                        any(Plan.class), any(CommercialCatalogResolver.PriceTuple.class),
                        org.mockito.ArgumentMatchers.anySet(), org.mockito.ArgumentMatchers.anyList(),
                        any(CommercialCatalogResolver.Audience.class)))
                .thenAnswer(invocation -> successfulResolution(
                        invocation.getArgument(0), invocation.getArgument(2), invocation.getArgument(3)));
        lenient().when(commercialCatalogResolver.resolveSelection(
                        any(Plan.class), any(CommercialCatalogResolver.PriceTuple.class),
                        org.mockito.ArgumentMatchers.anySet(), org.mockito.ArgumentMatchers.anyList(),
                        any(CommercialCatalogResolver.Audience.class),
                        any(CommercialCatalogResolver.RetainedSelection.class)))
                .thenAnswer(invocation -> successfulResolution(
                        invocation.getArgument(0), invocation.getArgument(2), invocation.getArgument(3)));
        lenient().when(commercialCatalogResolver.resolveSelection(
                        any(Plan.class), any(ProductPrice.class),
                        org.mockito.ArgumentMatchers.anySet(), org.mockito.ArgumentMatchers.anyList(),
                        any(CommercialCatalogResolver.Audience.class)))
                .thenAnswer(invocation -> successfulResolution(
                        invocation.getArgument(0), invocation.getArgument(2), invocation.getArgument(3)));
    }

    @Test
    void updateOverridesRejectsInvalidConfigurationBeforePersistence() {
        UUID accountId = UUID.randomUUID();
        Plan plan = plan("PRO", true);
        ReflectionTestUtils.setField(plan, "id", UUID.randomUUID());
        Subscription subscription = subscription(plan, SubscriptionStatus.ACTIVE);
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", accountId);
        subscription.setAccount(account);
        when(subscriptionRepository.findActiveByAccountId(accountId)).thenReturn(Optional.of(subscription));
        when(accountRepository.findByIdForSubscriptionUpdate(accountId)).thenReturn(Optional.of(account));
        var blocked = blockedResolution(
                plan, Set.of("MISSING_ADDON"), List.of(), ExtensionAvailabilityReason.PRODUCT_NOT_FOUND);
        when(commercialSelectionFinalizer.finalizeSelection(
                eq("PRO"), nullable(com.hiveapp.platform.client.plan.dto.ProductPriceSelectionRequest.class),
                eq(Set.of("MISSING_ADDON")), eq(List.of()),
                eq(CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR),
                any(CommercialCatalogResolver.RetainedSelection.class), eq(subscription.getEntitlementSnapshot())))
                .thenReturn(finalized(plan, blocked, subscription.getEntitlementSnapshot()));

        assertThatThrownBy(() -> subscriptionService.updateOverrides(accountId, Set.of("MISSING_ADDON"), List.of()))
                .isInstanceOf(OperationBlockedException.class)
                .hasMessage("The requested commercial selection is unavailable.");

        verify(subscriptionOverrideReader, never()).write(org.mockito.ArgumentMatchers.any());
        verify(subscriptionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateOverridesPersistsValidatedQuotaPackageSelection() {
        UUID accountId = UUID.randomUUID();
        Plan plan = plan("PRO", true);
        ReflectionTestUtils.setField(plan, "id", UUID.randomUUID());
        Subscription subscription = subscription(plan, SubscriptionStatus.ACTIVE);
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", accountId);
        subscription.setAccount(account);
        List<QuotaPackageSelection> quotaPackages = List.of(new QuotaPackageSelection("MEMBERS_10", 1));
        var snapshot = new SubscriptionEntitlementSnapshot(
                "PRO", BigDecimal.ZERO, "USD", BillingCycle.MONTHLY,
                List.of(new SubscriptionFeatureSnapshot(
                        StaffFeature.CODE, List.of(new QuotaLimitEntry(StaffFeature.MEMBERS, 3L)))),
                List.of());
        var currentSnapshot = subscription.getEntitlementSnapshot();
        when(accountRepository.findByIdForSubscriptionUpdate(accountId)).thenReturn(Optional.of(account));
        when(subscriptionRepository.findActiveByAccountId(accountId)).thenReturn(Optional.of(subscription));
        var resolved = successfulResolution(plan, Set.of(), quotaPackages);
        when(commercialSelectionFinalizer.finalizeSelection(
                eq("PRO"), nullable(com.hiveapp.platform.client.plan.dto.ProductPriceSelectionRequest.class),
                eq(Set.of()), eq(quotaPackages),
                eq(CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR),
                any(CommercialCatalogResolver.RetainedSelection.class), eq(currentSnapshot)))
                .thenReturn(finalized(plan, resolved, snapshot));
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
        var selection = new QuotaPackageSelection("MEMBERS_10", 2);

        when(subscriptionRepository.findActiveByAccountId(accountId))
                .thenReturn(Optional.of(subscription(plan, SubscriptionStatus.ACTIVE)));
        allowClientPlan(plan, List.of(), List.of(), List.of());
        when(commercialCatalogResolver.resolveSelection(
                eq(plan), any(CommercialCatalogResolver.PriceTuple.class), eq(Set.of()),
                eq(List.of(selection)), eq(CommercialCatalogResolver.Audience.CLIENT_CATALOG),
                any(CommercialCatalogResolver.RetainedSelection.class)))
                .thenReturn(blockedResolution(
                        plan, Set.of(), List.of(selection), ExtensionAvailabilityReason.INVALID_QUANTITY));

        assertThatThrownBy(() -> subscriptionService.previewChange(
                accountId, new SubscriptionChangeRequest("PRO", Set.of(), List.of(selection))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("The requested commercial selection is unavailable.");
    }

    @Test
    void planAssignmentCancelsExistingUsableSubscriptionsBeforeCreatingReplacement() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account();
        Plan free = plan("FREE", true);
        Plan pro = plan("PRO", true);
        Subscription active = subscription(free, SubscriptionStatus.ACTIVE);
        Subscription trialing = subscription(free, SubscriptionStatus.TRIALING);
        var snapshot = SubscriptionEntitlementSnapshot.empty(
                "PRO", BigDecimal.ZERO, "USD", BillingCycle.MONTHLY);

        when(accountRepository.findByIdForSubscriptionUpdate(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        var finalized = finalized(pro, successfulResolution(pro, Set.of(), List.of()), snapshot);
        when(commercialSelectionFinalizer.finalizeSelection(
                eq("PRO"), nullable(com.hiveapp.platform.client.plan.dto.ProductPriceSelectionRequest.class),
                eq(Set.of()), eq(List.of()), eq(CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR),
                eq(CommercialCatalogResolver.RetainedSelection.none()), nullable(SubscriptionEntitlementSnapshot.class)))
                .thenReturn(finalized);
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
        Plan archived = plan("ARCHIVED", false);
        when(accountRepository.findByIdForSubscriptionUpdate(accountId)).thenReturn(Optional.of(new Account()));
        when(commercialSelectionFinalizer.finalizeSelection(
                eq("ARCHIVED"), nullable(com.hiveapp.platform.client.plan.dto.ProductPriceSelectionRequest.class),
                eq(Set.of()), eq(List.of()), eq(CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR),
                eq(CommercialCatalogResolver.RetainedSelection.none()), nullable(SubscriptionEntitlementSnapshot.class)))
                .thenReturn(finalized(
                        archived,
                        blockedPlanResolution(archived, ExtensionAvailabilityReason.PRODUCT_NOT_ACTIVE),
                        SubscriptionEntitlementSnapshot.empty(
                                "ARCHIVED", BigDecimal.ZERO, "USD", BillingCycle.MONTHLY)));

        assertThatThrownBy(() -> subscriptionService.createSubscription(accountId, "ARCHIVED"))
                .isInstanceOf(OperationBlockedException.class)
                .hasMessageContaining("unavailable");

        verify(subscriptionRepository, never()).findAllByAccountIdAndStatusIn(
                any(), org.mockito.ArgumentMatchers.anyCollection());
        verify(subscriptionRepository, never()).saveAndFlush(any(Subscription.class));
    }

    @Test
    void planAssignmentRejectsReassigningTheCurrentActivePlan() {
        UUID accountId = UUID.randomUUID();
        Plan pro = plan("PRO", true);
        Account account = new Account();
        when(accountRepository.findByIdForSubscriptionUpdate(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(commercialSelectionFinalizer.finalizeSelection(
                eq("PRO"), nullable(com.hiveapp.platform.client.plan.dto.ProductPriceSelectionRequest.class),
                eq(Set.of()), eq(List.of()), eq(CommercialCatalogResolver.Audience.AUTHORIZED_OPERATOR),
                eq(CommercialCatalogResolver.RetainedSelection.none()), nullable(SubscriptionEntitlementSnapshot.class)))
                .thenReturn(finalized(
                        pro, successfulResolution(pro, Set.of(), List.of()),
                        SubscriptionEntitlementSnapshot.empty(
                                "PRO", BigDecimal.ZERO, "USD", BillingCycle.MONTHLY)));
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

        when(subscriptionRepository.findActiveByAccountId(accountId))
                .thenReturn(Optional.of(subscription(plan("FREE", true), SubscriptionStatus.ACTIVE)));
        allowClientPlan(pro, List.of(), List.of(), List.of());
        when(commercialCatalogResolver.resolveSelection(
                eq(pro), any(CommercialCatalogResolver.PriceTuple.class), eq(Set.of("MISSING_ADDON")),
                eq(List.of()), eq(CommercialCatalogResolver.Audience.CLIENT_CATALOG),
                any(CommercialCatalogResolver.RetainedSelection.class)))
                .thenReturn(blockedResolution(
                        pro, Set.of("MISSING_ADDON"), List.of(),
                        ExtensionAvailabilityReason.PRODUCT_NOT_FOUND));

        assertThatThrownBy(() -> subscriptionService.previewChange(
                accountId,
                new SubscriptionChangeRequest("PRO", Set.of("MISSING_ADDON"), List.of())))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("The requested commercial selection is unavailable.");
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
                        StaffFeature.CODE,
                        List.of(new QuotaLimitEntry(StaffFeature.MEMBERS, 10L)))),
                List.of());
        current.setEntitlementSnapshot(currentSnapshot);
        current.setCurrentMoney(Money.zero("USD"));
        PlanFeature workspace = planFeature(pro, StaffFeature.CODE, null,
                List.of(new QuotaLimitEntry(StaffFeature.MEMBERS, 2L)));
        SubscriptionEntitlementSnapshot targetSnapshot = new SubscriptionEntitlementSnapshot(
                "PRO",
                BigDecimal.ZERO,
                "USD",
                BillingCycle.MONTHLY,
                List.of(new SubscriptionFeatureSnapshot(
                        StaffFeature.CODE,
                        List.of(new QuotaLimitEntry(StaffFeature.MEMBERS, 2L)))),
                List.of());

        when(subscriptionRepository.findActiveByAccountId(accountId)).thenReturn(Optional.of(current));
        allowClientPlan(pro, List.of(workspace), List.of(), List.of());
        when(subscriptionSnapshotFactory.fromPlan(
                eq(pro), eq(Set.of()), eq(List.of()), any(ProductPrice.class))).thenReturn(targetSnapshot);
        when(subscriptionImpactAnalyzer.analyze(accountId, current, targetSnapshot))
                .thenReturn(List.of(new com.hiveapp.platform.client.plan.dto.SubscriptionChangeConflict(
                        "QUOTA_BELOW_USAGE", StaffFeature.CODE, StaffFeature.MEMBERS,
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
                free, StaffFeature.CODE, PlanFeatureMode.OPTIONAL_ADD_ON, List.of());
        AddOn addOn = addOn("EXTRA_MEMBERS");
        SubscriptionEntitlementSnapshot snapshot = new SubscriptionEntitlementSnapshot(
                "FREE", BigDecimal.ZERO, "USD", BillingCycle.MONTHLY,
                List.of(new SubscriptionFeatureSnapshot(StaffFeature.CODE, List.of())),
                List.of(new SubscriptionAddOnSnapshot(
                        "EXTRA_MEMBERS", "Extra members", 1, BigDecimal.TEN, "USD",
                        BillingCycle.MONTHLY, List.of(StaffFeature.CODE))));

        when(subscriptionRepository.findActiveByAccountId(accountId)).thenReturn(Optional.of(current));
        allowClientPlan(free, List.of(optional), List.of(), List.of());
        when(subscriptionSnapshotFactory.fromPlanPreservingPrices(
                eq(free), eq(Set.of("EXTRA_MEMBERS")), eq(List.of()), eq(current.getEntitlementSnapshot())))
                .thenReturn(snapshot);
        when(billingCalculator.calculateMoney(any())).thenReturn(Money.of(BigDecimal.TEN, "USD"));

        var preview = subscriptionService.previewChange(
                accountId, new SubscriptionChangeRequest("FREE", Set.of("EXTRA_MEMBERS"), List.of()));

        assertThat(preview.immediateAllowed()).isTrue();
        assertThat(preview.addOnCodes()).containsExactly("EXTRA_MEMBERS");
        assertThat(preview.effectiveFeatureCodes()).containsExactly(StaffFeature.CODE);
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
                plan, StaffFeature.CODE, PlanFeatureMode.OPTIONAL_ADD_ON, List.of());
        optional.getFeature().setStatus(FeatureStatus.INTERNAL);
        AddOn addOn = addOn("EXTRA_MEMBERS");

        when(subscriptionRepository.findActiveByAccountId(accountId)).thenReturn(Optional.of(current));
        when(subscriptionOverrideReader.read(current.getCustomOverrides()))
                .thenReturn(SubscriptionOverrides.empty());
        when(featureDefinitionCollectorProvider.getObject()).thenReturn(featureDefinitionCollector);
        when(featureDefinitionCollector.collectByCode())
                .thenReturn(Map.of(StaffFeature.CODE, StaffFeature.definition()));
        allowClientPlan(plan, List.of(optional), List.of(new CommercialCatalogResolver.AddOnResolution(
                addOn,
                List.of(new com.hiveapp.platform.client.plan.dto.ExtensionAvailabilityIssue(
                        ExtensionAvailabilityReason.FEATURE_NOT_CLIENT_FACING,
                        ExtensionResolutionSource.REGISTRY,
                        StaffFeature.CODE)),
                List.of(), Set.of(), Set.of())), List.of());

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
        PlanFeature workspace = planFeature(pro, StaffFeature.CODE, null,
                List.of(new QuotaLimitEntry(StaffFeature.MEMBERS, 10L)));
        SubscriptionEntitlementSnapshot targetSnapshot = new SubscriptionEntitlementSnapshot(
                "PRO",
                BigDecimal.valueOf(29),
                "USD",
                BillingCycle.MONTHLY,
                List.of(new SubscriptionFeatureSnapshot(
                        StaffFeature.CODE,
                        List.of(new QuotaLimitEntry(StaffFeature.MEMBERS, 10L)))),
                List.of());

        when(accountRepository.findByIdForSubscriptionUpdate(accountId)).thenReturn(Optional.of(account));
        when(subscriptionRepository.findActiveByAccountId(accountId)).thenReturn(Optional.of(current));
        allowClientPlan(pro, List.of(workspace), List.of(), List.of());
        when(commercialSelectionFinalizer.finalizeSelection(
                eq("PRO"), nullable(com.hiveapp.platform.client.plan.dto.ProductPriceSelectionRequest.class),
                eq(Set.of()), eq(List.of()), eq(CommercialCatalogResolver.Audience.CLIENT_CATALOG),
                any(CommercialCatalogResolver.RetainedSelection.class), eq(current.getEntitlementSnapshot())))
                .thenReturn(finalized(pro, successfulResolution(pro, Set.of(), List.of()), targetSnapshot));
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

    private void allowClientPlan(
            Plan plan,
            List<PlanFeature> features,
            List<CommercialCatalogResolver.AddOnResolution> addOns,
            List<CommercialCatalogResolver.QuotaPackageResolution> packages
    ) {
        when(commercialCatalogResolver.resolveCatalog(
                CommercialCatalogResolver.Audience.CLIENT_CATALOG))
                .thenReturn(new CommercialCatalogResolver.CatalogResolution(List.of(
                        planResolution(plan, features, addOns, packages))));
    }

    private CommercialCatalogResolver.SelectionResolution successfulResolution(
            Plan plan,
            Set<String> addOnCodes,
            List<QuotaPackageSelection> packages
    ) {
        return new CommercialCatalogResolver.SelectionResolution(
                planResolution(plan, List.of(), List.of(), List.of()),
                addOnCodes,
                packages,
                List.of(),
                Map.of());
    }

    private CommercialCatalogResolver.SelectionResolution blockedResolution(
            Plan plan,
            Set<String> addOnCodes,
            List<QuotaPackageSelection> packages,
            ExtensionAvailabilityReason reason
    ) {
        return new CommercialCatalogResolver.SelectionResolution(
                planResolution(plan, List.of(), List.of(), List.of()),
                addOnCodes,
                packages,
                List.of(new com.hiveapp.platform.client.plan.dto.ExtensionAvailabilityIssue(
                        reason, ExtensionResolutionSource.PRODUCT_LIFECYCLE, "test-product")),
                Map.of());
    }

    private CommercialCatalogResolver.SelectionResolution blockedPlanResolution(
            Plan plan,
            ExtensionAvailabilityReason reason
    ) {
        var issue = new com.hiveapp.platform.client.plan.dto.ExtensionAvailabilityIssue(
                reason, ExtensionResolutionSource.PRODUCT_LIFECYCLE, plan.getCode());
        return new CommercialCatalogResolver.SelectionResolution(
                new CommercialCatalogResolver.PlanResolution(
                        plan, List.of(issue), List.of(), List.of(), List.of(), List.of(),
                        plan.getExtensionPolicy(), plan.getSalesVisibility()),
                Set.of(), List.of(), List.of(), Map.of());
    }

    private CommercialSelectionFinalizer.FinalizedSelection finalized(
            Plan plan,
            CommercialCatalogResolver.SelectionResolution resolution,
            SubscriptionEntitlementSnapshot snapshot
    ) {
        return new CommercialSelectionFinalizer.FinalizedSelection(
                plan, testPrice(plan), resolution, snapshot);
    }

    private ProductPrice testPrice(Plan plan) {
        ProductPrice price = ProductPrice.draft(
                plan, plan.money(), plan.getBillingCycle(), java.time.Instant.EPOCH, null);
        price.activate();
        ReflectionTestUtils.setField(price, "id", UUID.randomUUID());
        return price;
    }

    private CommercialCatalogResolver.PlanResolution planResolution(
            Plan plan,
            List<PlanFeature> features,
            List<CommercialCatalogResolver.AddOnResolution> addOns,
            List<CommercialCatalogResolver.QuotaPackageResolution> packages
    ) {
        return new CommercialCatalogResolver.PlanResolution(
                plan, List.of(), List.of(), features, addOns, packages,
                plan.getExtensionPolicy(), plan.getSalesVisibility());
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

}
