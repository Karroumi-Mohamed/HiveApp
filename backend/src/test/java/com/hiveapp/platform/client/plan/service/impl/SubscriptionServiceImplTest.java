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
import com.hiveapp.platform.client.plan.domain.repository.ProductPriceRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeOperationRepository;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeRequest;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeApplyRequest;
import com.hiveapp.platform.client.plan.dto.QuotaPackageSelection;
import com.hiveapp.platform.client.plan.service.BillingCalculator;
import com.hiveapp.platform.client.plan.service.CommercialCatalogResolver;
import com.hiveapp.platform.client.plan.service.CommercialPolicyEvaluator;
import com.hiveapp.platform.client.plan.service.CommercialPolicySelectionPlanner;
import com.hiveapp.platform.client.plan.service.CommercialPolicySubscriptionTermsService;
import com.hiveapp.platform.client.plan.service.CommercialSelectionFinalizer;
import com.hiveapp.platform.client.plan.service.CommercialCatalogVersionService;
import com.hiveapp.platform.client.plan.service.CommercialPreviewTokenService;
import com.hiveapp.platform.client.plan.service.SubscriptionOverrideReader;
import com.hiveapp.platform.client.plan.service.SubscriptionSnapshotFactory;
import com.hiveapp.platform.client.plan.service.SubscriptionSnapshotReader;
import com.hiveapp.platform.client.plan.service.SubscriptionImpactAnalyzer;
import com.hiveapp.platform.client.plan.service.SubscriptionPeriodCalculator;
import com.hiveapp.platform.client.plan.service.SubscriptionCheckoutService;
import com.hiveapp.platform.client.plan.service.SubscriptionChangeActivationService;
import com.hiveapp.platform.client.plan.service.CommercialOfferRedemptionTransitionService;
import com.hiveapp.platform.client.plan.service.SubscriptionChangeOperationProjectionMapper;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeTiming;
import com.hiveapp.platform.client.plan.dto.ClientSubscriptionChangePreviewResponse;
import com.hiveapp.platform.client.plan.dto.EffectiveQuotaLimit;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionFeatureSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionAddOnSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionQuotaPackageSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionCommercialPolicyEvaluation;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeOperationDto;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrides;
import com.hiveapp.platform.registry.definition.FeatureDefinitionCollector;
import com.hiveapp.platform.registry.definition.StaffFeature;
import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.service.RegistryCatalogVersionService;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.exception.StaleResourceVersionException;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import com.hiveapp.shared.quota.QuotaLimitMode;
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
import java.time.Clock;
import java.time.Instant;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceImplTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000111");
    private static final Instant NOW = Instant.parse("2026-08-27T00:00:00Z");

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PlanRepository planRepository;
    @Mock private ProductPriceRepository productPriceRepository;
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
    @Mock private SubscriptionPeriodCalculator subscriptionPeriodCalculator;
    @Mock private SubscriptionChangeOperationRepository subscriptionChangeOperationRepository;
    @Mock private com.hiveapp.platform.client.plan.domain.repository.CommercialOfferRedemptionRepository commercialOfferRedemptionRepository;
    @Mock private CommercialOfferRedemptionTransitionService commercialOfferRedemptionTransitions;
    @Mock private SubscriptionCheckoutService subscriptionCheckoutService;
    @Mock private SubscriptionChangeOperationProjectionMapper operationProjectionMapper;
    @Mock private SubscriptionChangeActivationService subscriptionChangeActivationService;
    @Mock private com.hiveapp.platform.client.plan.service.ProductPriceResolver productPriceResolver;
    @Mock private CommercialCatalogResolver commercialCatalogResolver;
    @Mock private CommercialSelectionFinalizer commercialSelectionFinalizer;
    @Mock private CommercialPolicyEvaluator commercialPolicyEvaluator;
    @Mock private CommercialPolicySelectionPlanner commercialPolicySelectionPlanner;
    @Mock private CommercialPolicySubscriptionTermsService commercialPolicyTermsService;
    @Mock private CommercialCatalogVersionService commercialCatalogVersionService;
    @Mock private RegistryCatalogVersionService registryCatalogVersionService;
    @Mock private CommercialPreviewTokenService commercialPreviewTokenService;
    @Mock private Clock clock;

    @InjectMocks
    private SubscriptionServiceImpl subscriptionService;

    @BeforeEach
    void defaultPriceResolution() {
        lenient().when(registryCatalogVersionService.currentVersion()).thenReturn("registry:1");
        lenient().when(commercialCatalogVersionService.currentRevision()).thenReturn(1L);
        lenient().when(commercialCatalogVersionService.readConsistently(
                        org.mockito.ArgumentMatchers.<java.util.function.LongFunction<Object>>any()))
                .thenAnswer(invocation -> invocation
                        .<java.util.function.LongFunction<Object>>getArgument(0).apply(1L));
        lenient().when(clock.instant()).thenReturn(NOW);
        lenient().when(subscriptionPeriodCalculator.change(
                        any(BillingCycle.class), any(SubscriptionChangeTiming.class),
                        nullable(Instant.class)))
                .thenReturn(period());
        lenient().when(commercialPolicyEvaluator.evaluate(any(), any()))
                .thenAnswer(invocation -> CommercialPolicyEvaluator.Evaluation.empty(
                        invocation.getArgument(1)));
        lenient().when(commercialPreviewTokenService.issue(
                        any(), any(), org.mockito.ArgumentMatchers.anyLong(), any(),
                        org.mockito.ArgumentMatchers.anyLong(), any(), any(), any()))
                .thenReturn(new CommercialPreviewTokenService.IssuedEvidence(
                        "preview-token", NOW, NOW.plusSeconds(300)));
        lenient().when(commercialPreviewTokenService.requireValid(
                        any(), any(), any(), org.mockito.ArgumentMatchers.anyLong(), any(),
                        org.mockito.ArgumentMatchers.anyLong(), any(), any(), any()))
                .thenReturn(new CommercialPreviewTokenService.VerifiedEvidence(
                        NOW, NOW.plusSeconds(300)));
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
        lenient().when(commercialPolicySelectionPlanner.plan(
                        any(Plan.class), any(CommercialCatalogResolver.PriceTuple.class),
                        org.mockito.ArgumentMatchers.anySet(), org.mockito.ArgumentMatchers.anyList(),
                        any(CommercialCatalogResolver.Audience.class),
                        any(CommercialCatalogResolver.RetainedSelection.class),
                        any(CommercialPolicyEvaluator.Evaluation.class)))
                .thenAnswer(invocation -> {
                    Plan plan = invocation.getArgument(0);
                    CommercialCatalogResolver.PriceTuple tuple = invocation.getArgument(1);
                    Set<String> addOns = invocation.getArgument(2);
                    List<QuotaPackageSelection> packages = invocation.getArgument(3);
                    CommercialCatalogResolver.Audience audience = invocation.getArgument(4);
                    CommercialCatalogResolver.RetainedSelection retained = invocation.getArgument(5);
                    var resolution = commercialCatalogResolver.resolveSelection(
                            plan, tuple, addOns, packages, audience, retained);
                    return new CommercialPolicySelectionPlanner.PlannedSelection(
                            resolution, addOns, packages, List.of(), List.of());
                });
        lenient().when(commercialPolicyTermsService.apply(
                        any(Plan.class), any(SubscriptionEntitlementSnapshot.class),
                        any(CommercialPolicyEvaluator.Evaluation.class),
                        any(CommercialPolicySelectionPlanner.PlannedSelection.class)))
                .thenAnswer(invocation -> {
                    SubscriptionEntitlementSnapshot snapshot = invocation.getArgument(1);
                    SubscriptionCommercialPolicyEvaluation evaluation =
                            new SubscriptionCommercialPolicyEvaluation(
                                    NOW, snapshot.basePrice(), snapshot.basePrice(), BigDecimal.ZERO,
                                    snapshot.basePrice(), snapshot.currencyCode(), List.of(), List.of());
                    return new CommercialPolicySubscriptionTermsService.AppliedTerms(
                            snapshot.withCommercialPolicyEvaluation(evaluation), evaluation);
                });
        lenient().when(operationProjectionMapper.internal(any()))
                .thenAnswer(invocation -> {
                    SubscriptionChangeOperation operation = invocation.getArgument(0);
                    return new SubscriptionChangeOperationDto(
                            operation.getId(), operation.getCreatedAt(), operation.getUpdatedAt(),
                            operation.getTiming(), operation.getStatus(), operation.getEffectiveAt(),
                            operation.getSourceSubscription().getPlan().getCode(),
                            operation.getTargetPlan().getCode(), operation.getAttentionReason(), null,
                            operation.getCommercialPolicyEvaluation());
                });
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
                accountId, ACTOR_ID,
                new SubscriptionChangeRequest("PRO", Set.of(), List.of(selection))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("The requested commercial selection is unavailable.");
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
                ACTOR_ID,
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
        when(subscriptionSnapshotFactory.fromResolvedSelection(
                eq(pro), any(ProductPrice.class),
                any(CommercialCatalogResolver.SelectionResolution.class), eq(currentSnapshot)))
                .thenReturn(targetSnapshot);
        when(subscriptionImpactAnalyzer.analyze(
                eq(accountId), eq(current), any(SubscriptionEntitlementSnapshot.class)))
                .thenReturn(List.of(new com.hiveapp.platform.client.plan.dto.SubscriptionChangeConflict(
                        "QUOTA_BELOW_USAGE", StaffFeature.CODE, StaffFeature.MEMBERS,
                        3L, 2L, "Current usage is above the requested limit.")));
        when(billingCalculator.calculateMoney(any())).thenReturn(Money.zero("USD"));

        var preview = subscriptionService.previewChange(
                accountId,
                ACTOR_ID,
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
        when(subscriptionSnapshotFactory.fromResolvedSelection(
                eq(free), any(ProductPrice.class),
                any(CommercialCatalogResolver.SelectionResolution.class),
                eq(current.getEntitlementSnapshot())))
                .thenReturn(snapshot);
        when(billingCalculator.calculateMoney(any())).thenReturn(Money.of(BigDecimal.TEN, "USD"));

        var preview = subscriptionService.previewChange(
                accountId, ACTOR_ID,
                new SubscriptionChangeRequest("FREE", Set.of("EXTRA_MEMBERS"), List.of()));

        assertThat(preview.immediateAllowed()).isTrue();
        assertThat(preview.addOnCodes()).containsExactly("EXTRA_MEMBERS");
        assertThat(preview.effectiveFeatureCodes()).containsExactly(StaffFeature.CODE);
        assertThat(preview.previewPrice()).isEqualByComparingTo("10");
    }

    @Test
    void previewProjectsSafeCurrentAndTargetEntitlementsWithAuthoritativeRenewalPeriod() {
        UUID accountId = UUID.randomUUID();
        Instant renewalAt = Instant.parse("2026-09-01T00:00:00Z");
        Instant renewalUntil = Instant.parse("2027-09-01T00:00:00Z");
        Plan free = plan("FREE", true);
        Plan pro = plan("PRO", true);
        pro.setBillingCycle(BillingCycle.YEARLY);
        ReflectionTestUtils.setField(pro, "id", UUID.randomUUID());

        SubscriptionEntitlementSnapshot currentSnapshot = new SubscriptionEntitlementSnapshot(
                SubscriptionEntitlementSnapshot.CURRENT_SCHEMA_VERSION,
                "FREE", "Free", 4L, BigDecimal.ZERO, "USD", BillingCycle.MONTHLY,
                NOW.minusSeconds(2_592_000), renewalAt,
                List.of(new SubscriptionFeatureSnapshot(
                        StaffFeature.CODE,
                        List.of(new QuotaLimitEntry(StaffFeature.MEMBERS, 5L)))),
                List.of(new SubscriptionAddOnSnapshot(
                        "CURRENT_ADDON", "Current AddOn", 2L, BigDecimal.ONE, "USD",
                        BillingCycle.MONTHLY, List.of(StaffFeature.CODE), UUID.randomUUID())),
                List.of(new SubscriptionQuotaPackageSnapshot(
                        "CURRENT_CAPACITY", "Current capacity", 2L, StaffFeature.CODE,
                        StaffFeature.MEMBERS, 5L, 2, BigDecimal.ONE, "USD",
                        BillingCycle.MONTHLY, UUID.randomUUID())),
                UUID.randomUUID(), null, null);
        Subscription current = subscription(free, SubscriptionStatus.ACTIVE);
        current.setEntitlementSnapshot(currentSnapshot);
        current.setCurrentPeriodStart(NOW.minusSeconds(2_592_000));
        current.setCurrentPeriodEnd(renewalAt);
        current.setCurrentMoney(Money.zero("USD"));

        var targetSelection = new QuotaPackageSelection("TARGET_CAPACITY", 3);
        SubscriptionEntitlementSnapshot targetSnapshot = new SubscriptionEntitlementSnapshot(
                SubscriptionEntitlementSnapshot.CURRENT_SCHEMA_VERSION,
                "PRO", "Pro", 7L, BigDecimal.valueOf(120), "USD", BillingCycle.YEARLY,
                null, null,
                List.of(new SubscriptionFeatureSnapshot(
                        StaffFeature.CODE,
                        List.of(new QuotaLimitEntry(StaffFeature.MEMBERS, 20L)))),
                List.of(new SubscriptionAddOnSnapshot(
                        "TARGET_ADDON", "Target AddOn", 3L, BigDecimal.TEN, "USD",
                        BillingCycle.YEARLY, List.of(StaffFeature.CODE), UUID.randomUUID())),
                List.of(new SubscriptionQuotaPackageSnapshot(
                        targetSelection.packageCode(), "Target capacity", 3L, StaffFeature.CODE,
                        StaffFeature.MEMBERS, 10L, targetSelection.quantity(), BigDecimal.TEN, "USD",
                        BillingCycle.YEARLY, UUID.randomUUID())),
                UUID.randomUUID(), null, null);
        var currentLimits = List.of(new EffectiveQuotaLimit(
                StaffFeature.CODE, StaffFeature.MEMBERS, QuotaLimitMode.FINITE, 5L, 10L, 15L));
        var targetLimits = List.of(new EffectiveQuotaLimit(
                StaffFeature.CODE, StaffFeature.MEMBERS, QuotaLimitMode.FINITE, 20L, 30L, 50L));

        when(subscriptionRepository.findActiveByAccountId(accountId)).thenReturn(Optional.of(current));
        allowClientPlan(pro, List.of(), List.of(), List.of());
        when(subscriptionSnapshotFactory.fromResolvedSelection(
                eq(pro), any(ProductPrice.class),
                any(CommercialCatalogResolver.SelectionResolution.class), eq(currentSnapshot)))
                .thenReturn(targetSnapshot);
        when(subscriptionImpactAnalyzer.effectiveQuotaLimits(any(SubscriptionEntitlementSnapshot.class)))
                .thenAnswer(invocation -> "FREE".equals(
                        invocation.<SubscriptionEntitlementSnapshot>getArgument(0).planCode())
                        ? currentLimits : targetLimits);
        when(billingCalculator.calculateMoney(any())).thenReturn(Money.of(BigDecimal.valueOf(160), "USD"));
        when(subscriptionPeriodCalculator.change(
                BillingCycle.YEARLY, SubscriptionChangeTiming.AT_RENEWAL, renewalAt))
                .thenReturn(new SubscriptionPeriodCalculator.Period(renewalAt, renewalUntil));

        var preview = subscriptionService.previewChange(
                accountId, ACTOR_ID,
                new SubscriptionChangeRequest(
                        "PRO", Set.of("TARGET_ADDON"), List.of(targetSelection),
                        SubscriptionChangeTiming.AT_RENEWAL));

        assertThat(preview.timing()).isEqualTo(SubscriptionChangeTiming.AT_RENEWAL);
        assertThat(preview.effectiveAt()).isEqualTo(renewalAt);
        assertThat(preview.effectiveUntil()).isEqualTo(renewalUntil);
        assertThat(preview.currentEntitlements().planCode()).isEqualTo("FREE");
        assertThat(preview.currentEntitlements().billingCycle()).isEqualTo(BillingCycle.MONTHLY);
        assertThat(preview.currentEntitlements().featureCodes()).containsExactly(StaffFeature.CODE);
        assertThat(preview.currentEntitlements().effectiveQuotaLimits()).isEqualTo(currentLimits);
        assertThat(preview.currentEntitlements().addOnCodes()).containsExactly("CURRENT_ADDON");
        assertThat(preview.currentEntitlements().quotaPackages())
                .containsExactly(new QuotaPackageSelection("CURRENT_CAPACITY", 2));
        assertThat(preview.targetEntitlements().planCode()).isEqualTo("PRO");
        assertThat(preview.targetEntitlements().billingCycle()).isEqualTo(BillingCycle.YEARLY);
        assertThat(preview.targetEntitlements().featureCodes()).containsExactly(StaffFeature.CODE);
        assertThat(preview.targetEntitlements().effectiveQuotaLimits()).isEqualTo(targetLimits);
        assertThat(preview.targetEntitlements().addOnCodes()).containsExactly("TARGET_ADDON");
        assertThat(preview.targetEntitlements().quotaPackages()).containsExactly(targetSelection);
        assertThat(preview.effectiveFeatureCodes()).isEqualTo(preview.targetEntitlements().featureCodes());
        assertThat(preview.effectiveQuotaLimits())
                .isEqualTo(preview.targetEntitlements().effectiveQuotaLimits());

        var client = ClientSubscriptionChangePreviewResponse.from(preview);
        assertThat(client.timing()).isEqualTo(preview.timing());
        assertThat(client.effectiveAt()).isEqualTo(preview.effectiveAt());
        assertThat(client.effectiveUntil()).isEqualTo(preview.effectiveUntil());
        assertThat(client.currentEntitlements()).isEqualTo(preview.currentEntitlements());
        assertThat(client.targetEntitlements()).isEqualTo(preview.targetEntitlements());
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
                any(CommercialCatalogResolver.RetainedSelection.class), eq(current.getEntitlementSnapshot()),
                any(CommercialPolicyEvaluator.Evaluation.class)))
                .thenReturn(finalized(pro, successfulResolution(pro, Set.of(), List.of()), targetSnapshot));
        when(subscriptionSnapshotReader.read(current.getEntitlementSnapshot()))
                .thenReturn(Optional.of(SubscriptionEntitlementSnapshot.empty(
                        "FREE", BigDecimal.ZERO, "USD", BillingCycle.MONTHLY)));
        when(subscriptionOverrideReader.read(current.getCustomOverrides()))
                .thenReturn(SubscriptionOverrides.empty());
        when(billingCalculator.calculateMoney(any())).thenReturn(Money.of(BigDecimal.valueOf(29), "USD"));
        when(subscriptionPeriodCalculator.change(
                BillingCycle.MONTHLY, SubscriptionChangeTiming.IMMEDIATE, null))
                .thenReturn(period());
        when(subscriptionChangeOperationRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> {
                    var operation = invocation.getArgument(0, SubscriptionChangeOperation.class);
                    ReflectionTestUtils.setField(operation, "id", UUID.randomUUID());
                    return operation;
                });

        var response = subscriptionService.applyChange(
                accountId,
                actorUserId,
                new SubscriptionChangeApplyRequest(
                        new SubscriptionChangeRequest("PRO", Set.of(), List.of()),
                        "preview-token"));

        assertThat(current.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(response.subscription().plan().code()).isEqualTo("FREE");
        assertThat(response.operation().status()).isEqualTo(SubscriptionChangeStatus.AWAITING_CONFIRMATION);
        verify(subscriptionCheckoutService).initiate(
                any(), org.mockito.ArgumentMatchers.eq(Money.of(BigDecimal.valueOf(29), "USD")),
                org.mockito.ArgumentMatchers.eq(actorUserId));
        verify(registryCatalogVersionService).requireCurrent("registry:1");
        verify(subscriptionChangeActivationService, never()).activate(any(), any());
    }

    @Test
    void registryChangeDuringFinalizationReturnsStalePreviewWithoutWriting() {
        UUID accountId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", accountId);
        Plan currentPlan = plan("FREE", true);
        Plan targetPlan = plan("PRO", true);
        ReflectionTestUtils.setField(targetPlan, "id", UUID.randomUUID());
        Subscription current = subscription(currentPlan, SubscriptionStatus.ACTIVE);
        current.setAccount(account);
        current.setEntitlementSnapshot(SubscriptionEntitlementSnapshot.empty(
                "FREE", BigDecimal.ZERO, "USD", BillingCycle.MONTHLY));
        current.setCustomOverrides(SubscriptionOverrides.empty());
        current.setCurrentMoney(Money.zero("USD"));
        SubscriptionEntitlementSnapshot targetSnapshot = SubscriptionEntitlementSnapshot.empty(
                "PRO", BigDecimal.valueOf(29), "USD", BillingCycle.MONTHLY);

        when(accountRepository.findByIdForSubscriptionUpdate(accountId)).thenReturn(Optional.of(account));
        when(subscriptionRepository.findActiveByAccountId(accountId)).thenReturn(Optional.of(current));
        allowClientPlan(targetPlan, List.of(), List.of(), List.of());
        when(commercialSelectionFinalizer.finalizeSelection(
                eq("PRO"), nullable(com.hiveapp.platform.client.plan.dto.ProductPriceSelectionRequest.class),
                eq(Set.of()), eq(List.of()), eq(CommercialCatalogResolver.Audience.CLIENT_CATALOG),
                any(CommercialCatalogResolver.RetainedSelection.class), eq(current.getEntitlementSnapshot()),
                any(CommercialPolicyEvaluator.Evaluation.class)))
                .thenReturn(finalized(
                        targetPlan, successfulResolution(targetPlan, Set.of(), List.of()), targetSnapshot));
        when(billingCalculator.calculateMoney(any())).thenReturn(Money.of(BigDecimal.valueOf(29), "USD"));
        doThrow(new InvalidStateException("Registry catalog changed"))
                .when(registryCatalogVersionService).requireCurrent("registry:1");

        assertThatThrownBy(() -> subscriptionService.applyChange(
                accountId, actorUserId,
                new SubscriptionChangeApplyRequest(
                        new SubscriptionChangeRequest("PRO", Set.of(), List.of()),
                        "preview-token")))
                .isInstanceOf(StaleResourceVersionException.class)
                .hasMessage("Subscription-change preview is stale. Review the selection again and retry.");

        verify(subscriptionChangeOperationRepository, never()).saveAndFlush(any());
        verify(subscriptionCheckoutService, never()).initiate(any(), any(), any());
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
