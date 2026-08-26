package com.hiveapp.platform.client.plan.service.impl;

import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.AddOnCreationReason;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionCheckoutStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.entity.AddOn;
import com.hiveapp.platform.client.plan.domain.entity.AddOnFeature;
import com.hiveapp.platform.client.plan.domain.repository.AddOnFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeOperationRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionCheckoutRepository;
import com.hiveapp.platform.client.plan.domain.repository.QuotaPackageRepository;
import com.hiveapp.platform.client.plan.dto.AssignPlanFeatureRequest;
import com.hiveapp.platform.client.plan.dto.CreateAddOnRequest;
import com.hiveapp.platform.client.plan.dto.CreatePlanRequest;
import com.hiveapp.platform.client.plan.dto.DeletePlanRequest;
import com.hiveapp.platform.client.plan.dto.PlanBranchRequest;
import com.hiveapp.platform.client.plan.dto.UpdatePlanRequest;
import com.hiveapp.platform.client.plan.service.BillingConfigurationValidator;
import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.shared.exception.BusinessException;
import com.hiveapp.shared.exception.DuplicateResourceException;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import com.hiveapp.shared.money.Money;
import org.junit.jupiter.api.BeforeEach;
import com.hiveapp.platform.client.plan.dto.PlanDto;
import com.hiveapp.platform.client.plan.dto.AddOnDto;
import com.hiveapp.platform.client.plan.dto.QuotaPackageDto;
import com.hiveapp.platform.client.plan.service.PlanAdminReadModels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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
class PlanAdminServiceImplTest {

    @Mock private PlanRepository planRepository;
    @Mock private PlanFeatureRepository planFeatureRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionChangeOperationRepository subscriptionChangeOperationRepository;
    @Mock private SubscriptionCheckoutRepository subscriptionCheckoutRepository;
    @Mock private BillingConfigurationValidator billingConfigurationValidator;
    @Mock private AddOnRepository addOnRepository;
    @Mock private AddOnFeatureRepository addOnFeatureRepository;
    @Mock private QuotaPackageRepository quotaPackageRepository;
    // Real projection so these assertions also cover the read model the service now owns.
    @Spy private PlanAdminReadModels readModels = new PlanAdminReadModels();

    @InjectMocks
    private PlanAdminServiceImpl planAdminService;

    @BeforeEach
    void setUp() {
        lenientSavedPlan();
    }

    @Test
    void commercialOverviewSeparatesCurrentSubscriptionsAndAttentionWork() {
        when(planRepository.count()).thenReturn(8L);
        when(planRepository.countByStatus(PlanStatus.DRAFT)).thenReturn(2L);
        when(planRepository.countByStatus(PlanStatus.ACTIVE)).thenReturn(4L);
        when(planRepository.countByStatus(PlanStatus.INACTIVE)).thenReturn(1L);
        when(planRepository.countByStatus(PlanStatus.ARCHIVED)).thenReturn(1L);
        when(subscriptionRepository.countByStatus(SubscriptionStatus.ACTIVE)).thenReturn(10L);
        when(subscriptionRepository.countByStatus(SubscriptionStatus.TRIALING)).thenReturn(3L);
        when(subscriptionRepository.countByStatus(SubscriptionStatus.PAST_DUE)).thenReturn(2L);
        when(subscriptionRepository.countByStatus(SubscriptionStatus.SUSPENDED)).thenReturn(1L);
        when(subscriptionCheckoutRepository.countByStatus(SubscriptionCheckoutStatus.PENDING_CONFIRMATION))
                .thenReturn(4L);
        when(subscriptionChangeOperationRepository.countByStatus(SubscriptionChangeStatus.PENDING)).thenReturn(5L);
        when(subscriptionChangeOperationRepository.countByStatus(SubscriptionChangeStatus.NEEDS_ATTENTION))
                .thenReturn(2L);

        var result = planAdminService.getCommercialOverview();

        assertThat(result.totalPlans()).isEqualTo(8);
        assertThat(result.currentSubscriptions()).isEqualTo(16);
        assertThat(result.activeSubscriptions()).isEqualTo(10);
        assertThat(result.trialingSubscriptions()).isEqualTo(3);
        assertThat(result.pastDueSubscriptions()).isEqualTo(2);
        assertThat(result.suspendedSubscriptions()).isEqualTo(1);
        assertThat(result.pendingCheckouts()).isEqualTo(4);
        assertThat(result.scheduledChanges()).isEqualTo(5);
        assertThat(result.changesNeedingAttention()).isEqualTo(2);
    }

    @Test
    void createPlanIsAnExplicitEmptyNormalizedDraft() {
        PlanDto created = planAdminService.createPlan(new CreatePlanRequest(
                "Starter",
                null,
                BigDecimal.TEN,
                "USD",
                BillingCycle.MONTHLY
        ));

        assertThat(created.status()).isEqualTo(PlanStatus.DRAFT);
        assertThat(created.code()).startsWith("STARTER_");
        assertThat(created.revisionNumber()).isEqualTo(1);
        assertThat(created.sourcePlanId()).isNull();
        verify(planFeatureRepository, never()).saveAll(any());
    }

    @Test
    void duplicatePlanCopiesCompositionIntoAnIndependentDraftLineage() {
        UUID sourcePlanId = UUID.randomUUID();
        Plan sourcePlan = plan(sourcePlanId, "PRO");
        Feature workspace = feature("platform.workspace");
        PlanFeature sourceFeature = planFeature(sourcePlan, workspace,
                List.of(new QuotaLimitEntry("members", 3L)));

        when(planRepository.findById(sourcePlanId)).thenReturn(Optional.of(sourcePlan));
        when(planFeatureRepository.findAllByPlanId(sourcePlanId)).thenReturn(List.of(sourceFeature));

        PlanDto duplicate = planAdminService.duplicatePlan(sourcePlanId, new PlanBranchRequest(
                "Team",
                null,
                new BigDecimal("49.00"),
                "USD",
                BillingCycle.MONTHLY
        ));

        assertThat(duplicate.sourcePlanId()).isEqualTo(sourcePlan.getId());
        assertThat(duplicate.lineageId()).isNotEqualTo(sourcePlan.getLineageId());
        assertThat(duplicate.revisionNumber()).isEqualTo(1);
        assertThat(capturedInheritedFeatures()).singleElement()
                .satisfies(copy -> {
                    assertThat(copy.getFeature()).isSameAs(workspace);
                    assertThat(copy.getQuotaConfigs()).containsExactly(new QuotaLimitEntry("members", 3L));
                    assertThat(copy.getQuotaConfigs()).isNotSameAs(sourceFeature.getQuotaConfigs());
                });
    }

    @Test
    void revisePublishedPlanContinuesLineageAndCopiesAgainstTargetCurrency() {
        UUID sourcePlanId = UUID.randomUUID();
        Plan sourcePlan = plan(sourcePlanId, "PRO");
        sourcePlan.setRevisionNumber(3);
        PlanFeature includedFeature = planFeature(sourcePlan, feature("platform.workspace"), List.of());

        when(planRepository.findByIdForUpdate(sourcePlanId)).thenReturn(Optional.of(sourcePlan));
        when(planRepository.findMaximumRevisionNumber(sourcePlan.getLineageId())).thenReturn(3);
        when(planFeatureRepository.findAllByPlanId(sourcePlanId)).thenReturn(List.of(includedFeature));

        PlanDto created = planAdminService.revisePlan(sourcePlanId, new PlanBranchRequest(
                "Europe", null, BigDecimal.TEN, "EUR", BillingCycle.MONTHLY));

        assertThat(created.currencyCode()).isEqualTo("EUR");
        assertThat(created.lineageId()).isEqualTo(sourcePlan.getLineageId());
        assertThat(created.revisionNumber()).isEqualTo(4);
        assertThat(capturedInheritedFeatures()).hasSize(1);
        verify(billingConfigurationValidator).validatePlanFeature(
                "platform.workspace", PlanFeatureMode.INCLUDED, List.of(), "EUR");
    }

    @Test
    void assignFeatureRejectsConfigurationRejectedByBillingValidator() {
        UUID planId = UUID.randomUUID();
        String featureCode = "platform.plans";

        Plan draft = plan(planId);
        draft.setStatus(PlanStatus.DRAFT);
        when(planRepository.findById(planId)).thenReturn(Optional.of(draft));
        doThrow(new InvalidRequestException("Feature cannot be assigned to billing configuration."))
                .when(billingConfigurationValidator).validatePlanFeature(
                        featureCode, PlanFeatureMode.INCLUDED, List.of(), "USD");

        assertThatThrownBy(() -> planAdminService.assignFeature(
                planId,
                new AssignPlanFeatureRequest(featureCode, PlanFeatureMode.INCLUDED, List.of())
        ))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("billing configuration");

        verify(planFeatureRepository, never()).saveAndFlush(any(PlanFeature.class));
    }

    @Test
    void assignFeatureTranslatesDatabaseUniquenessRaceToDuplicateResource() {
        UUID planId = UUID.randomUUID();
        Plan plan = plan(planId);
        plan.setStatus(PlanStatus.DRAFT);
        Feature workspace = feature("platform.workspace");
        var request = new AssignPlanFeatureRequest("platform.workspace", PlanFeatureMode.INCLUDED, List.of());

        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(billingConfigurationValidator.validatePlanFeature(
                "platform.workspace", PlanFeatureMode.INCLUDED, List.of(), "USD")).thenReturn(workspace);
        when(planFeatureRepository.findByPlanIdAndFeature_Code(planId, "platform.workspace"))
                .thenReturn(Optional.empty());
        when(planFeatureRepository.saveAndFlush(any(PlanFeature.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate mapping"));

        assertThatThrownBy(() -> planAdminService.assignFeature(planId, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("PlanFeature already exists with featureCode = platform.workspace");
    }

    @Test
    void updateFeatureAppliesBillingValidationBeforeSavingQuotaConfiguration() {
        UUID planId = UUID.randomUUID();
        UUID planFeatureId = UUID.randomUUID();
        Feature feature = feature("platform.workspace");
        PlanFeature planFeature = new PlanFeature();
        Plan draft = plan(planId);
        draft.setStatus(PlanStatus.DRAFT);
        planFeature.setPlan(draft);
        planFeature.setFeature(feature);
        var request = new AssignPlanFeatureRequest("platform.workspace", PlanFeatureMode.INCLUDED, List.of());

        when(planFeatureRepository.findById(planFeatureId)).thenReturn(Optional.of(planFeature));
        when(planFeatureRepository.save(planFeature)).thenReturn(planFeature);

        planAdminService.updateFeature(planId, planFeatureId, request);

        verify(billingConfigurationValidator).validatePlanFeature(
                "platform.workspace", PlanFeatureMode.INCLUDED, List.of(), "USD");
        verify(planFeatureRepository).save(planFeature);
    }

    @Test
    void updatePlanRejectsForeverBillingCycleForNonFreePlan() {
        UUID planId = UUID.randomUUID();
        Plan draft = plan(planId, "PRO");
        draft.setStatus(PlanStatus.DRAFT);
        when(planRepository.findById(planId)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> planAdminService.updatePlan(
                planId,
                new UpdatePlanRequest("Pro", null, BigDecimal.TEN, "USD", BillingCycle.FOREVER)
        ))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Perpetual commercial plans are deferred; use MONTHLY or YEARLY.");

        verify(planRepository, never()).save(any(Plan.class));
    }

    @Test
    void updatePlanRejectsCurrencyChangeAfterSubscriptionHistoryExists() {
        UUID planId = UUID.randomUUID();
        Plan plan = plan(planId, "PRO");
        plan.setStatus(PlanStatus.DRAFT);
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.countByPlan_Id(planId)).thenReturn(1L);

        assertThatThrownBy(() -> planAdminService.updatePlan(
                planId,
                new UpdatePlanRequest("Pro", null, BigDecimal.TEN, "EUR", BillingCycle.MONTHLY)
        ))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Plan currency cannot change after subscription history exists.");

        verify(planRepository, never()).save(any(Plan.class));
    }

    @Test
    void updatePlanAllowsCurrencyChangeWithoutSubscriptionHistory() {
        UUID planId = UUID.randomUUID();
        Plan plan = plan(planId, "DRAFT");
        plan.setStatus(PlanStatus.DRAFT);
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        PlanDto updated = planAdminService.updatePlan(
                planId,
                new UpdatePlanRequest("Draft", null, BigDecimal.TEN, "EUR", BillingCycle.MONTHLY));

        assertThat(updated.currencyCode()).isEqualTo("EUR");
        assertThat(updated.price()).isEqualByComparingTo("10.00");
    }

    @Test
    void deletionPreviewExplainsPublishedAndSubscriptionHistoryBlockers() {
        UUID planId = UUID.randomUUID();
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan(planId, "PRO")));
        when(subscriptionRepository.countByPlan_Id(planId)).thenReturn(2L);

        var preview = planAdminService.previewPlanDeletion(planId);

        assertThat(preview.deletable()).isFalse();
        assertThat(preview.blockers()).contains("NOT_UNUSED_DRAFT", "SUBSCRIPTION_HISTORY");
        assertThat(preview.subscriptionHistoryCount()).isEqualTo(2);
    }

    @Test
    void defaultPlanCannotBeDeactivated() {
        UUID planId = UUID.randomUUID();
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan(planId, "FREE")));

        assertThatThrownBy(() -> planAdminService.transitionStatus(planId, PlanStatus.INACTIVE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("default FREE plan must remain ACTIVE");

        verify(planRepository, never()).save(any(Plan.class));
    }

    @Test
    void defaultPlanDeletionPreviewIsAlwaysBlocked() {
        UUID planId = UUID.randomUUID();
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan(planId, "FREE")));

        var preview = planAdminService.previewPlanDeletion(planId);

        assertThat(preview.deletable()).isFalse();
        assertThat(preview.blockers()).contains("DEFAULT_PROVISIONING_PLAN", "NOT_UNUSED_DRAFT");
    }

    @Test
    void draftPlanRequiresIncludedCompositionBeforeActivation() {
        UUID planId = UUID.randomUUID();
        Plan draft = plan(planId, "STARTER");
        draft.setStatus(PlanStatus.DRAFT);
        when(planRepository.findById(planId)).thenReturn(Optional.of(draft));
        when(planFeatureRepository.findAllByPlanId(planId)).thenReturn(List.of());

        assertThatThrownBy(() -> planAdminService.transitionStatus(planId, PlanStatus.ACTIVE))
                .isInstanceOf(BusinessException.class)
                .hasMessage("A Plan requires at least one included feature before activation.");
    }

    @Test
    void planLifecycleMakesArchivedStateTerminal() {
        UUID planId = UUID.randomUUID();
        Plan plan = plan(planId, "STARTER");
        plan.setStatus(PlanStatus.ARCHIVED);
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> planAdminService.transitionStatus(planId, PlanStatus.ACTIVE))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Archived plans are terminal and cannot transition.");
    }

    @Test
    void addOnDraftNormalizesIdentityAndCannotActivateWithoutFeatures() {
        when(planRepository.findByCode("FREE")).thenReturn(Optional.of(plan(UUID.randomUUID(), "FREE")));
        java.util.concurrent.atomic.AtomicReference<AddOn> savedEntity =
                new java.util.concurrent.atomic.AtomicReference<>();
        when(addOnRepository.save(any(AddOn.class))).thenAnswer(invocation -> {
            savedEntity.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });

        AddOnDto addOn = planAdminService.createAddOn(new CreateAddOnRequest(
                "Reporting", null, BigDecimal.TEN, "usd",
                BillingCycle.MONTHLY, Set.of("FREE"), Set.of(), Set.of(), Set.of()));

        assertThat(addOn.code()).startsWith("REPORTING_");
        assertThat(addOn.currencyCode()).isEqualTo("USD");
        assertThat(addOn.status()).isEqualTo(AddOnStatus.DRAFT);

        UUID addOnId = UUID.randomUUID();
        ReflectionTestUtils.setField(savedEntity.get(), "id", addOnId);
        when(addOnRepository.findById(addOnId)).thenReturn(Optional.of(savedEntity.get()));
        when(addOnFeatureRepository.findAllByAddOnId(addOnId)).thenReturn(List.of());

        assertThatThrownBy(() -> planAdminService.transitionAddOnStatus(addOnId, AddOnStatus.ACTIVE))
                .isInstanceOf(BusinessException.class)
                .hasMessage("An AddOn requires at least one feature before activation.");
    }

    @Test
    void publishedAddOnRevisionCopiesCommercialDefinitionIntoANewDraft() {
        UUID sourceId = UUID.randomUUID();
        UUID lineageId = UUID.randomUUID();
        AddOn source = new AddOn();
        ReflectionTestUtils.setField(source, "id", sourceId);
        source.setCode("REPORTING_R1");
        source.setName("Reporting");
        source.setDescription("Operational reports");
        source.setMoney(Money.of(BigDecimal.TEN, "USD"));
        source.setBillingCycle(BillingCycle.MONTHLY);
        source.setStatus(AddOnStatus.ACTIVE);
        source.setLineageId(lineageId);
        source.setRevisionNumber(1);
        source.setAllowedPlanCodes(Set.of("PRO"));

        Feature feature = feature("platform.company");
        AddOnFeature sourceFeature = new AddOnFeature();
        sourceFeature.setAddOn(source);
        sourceFeature.setFeature(feature);
        sourceFeature.setQuotaConfigs(List.of());

        when(addOnRepository.findByIdForUpdate(sourceId)).thenReturn(Optional.of(source));
        when(addOnRepository.findLineageForUpdate(lineageId)).thenReturn(List.of(source));
        when(addOnRepository.findMaximumRevisionNumber(lineageId)).thenReturn(1);
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(plan(UUID.randomUUID(), "PRO")));
        when(addOnRepository.saveAndFlush(any(AddOn.class))).thenAnswer(invocation -> {
            AddOn saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });
        when(addOnFeatureRepository.findAllByAddOnId(sourceId)).thenReturn(List.of(sourceFeature));

        AddOnDto revision = planAdminService.reviseAddOn(sourceId);

        assertThat(revision.status()).isEqualTo(AddOnStatus.DRAFT);
        assertThat(revision.lineageId()).isEqualTo(lineageId);
        assertThat(revision.revisionNumber()).isEqualTo(2);
        assertThat(revision.sourceAddOnId()).isEqualTo(sourceId);
        assertThat(revision.creationReason()).isEqualTo(AddOnCreationReason.REVISED);
        assertThat(revision.features()).extracting(AddOnDto.FeatureItem::featureCode)
                .containsExactly("platform.company");
        assertThat(source.getStatus()).isEqualTo(AddOnStatus.ACTIVE);
    }

    @Test
    void draftAddOnCannotBeArchivedInsteadOfPublishedOrDeleted() {
        UUID addOnId = UUID.randomUUID();
        AddOn draft = new AddOn();
        ReflectionTestUtils.setField(draft, "id", addOnId);
        draft.setStatus(AddOnStatus.DRAFT);
        when(addOnRepository.findById(addOnId)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> planAdminService.transitionAddOnStatus(addOnId, AddOnStatus.ARCHIVED))
                .isInstanceOf(BusinessException.class)
                .hasMessage("An AddOn draft must be published or deleted.");
    }

    @Test
    void archivedAddOnCannotBeUsedAsARevisionSource() {
        UUID addOnId = UUID.randomUUID();
        AddOn archived = new AddOn();
        ReflectionTestUtils.setField(archived, "id", addOnId);
        archived.setStatus(AddOnStatus.ARCHIVED);
        when(addOnRepository.findByIdForUpdate(addOnId)).thenReturn(Optional.of(archived));

        assertThatThrownBy(() -> planAdminService.reviseAddOn(addOnId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Archived AddOns are terminal and cannot be revised.");
        verify(addOnRepository, never()).saveAndFlush(any(AddOn.class));
    }

    @Test
    void addOnLineageCannotAccumulateCompetingDraftRevisions() {
        UUID sourceId = UUID.randomUUID();
        UUID lineageId = UUID.randomUUID();
        AddOn source = new AddOn();
        ReflectionTestUtils.setField(source, "id", sourceId);
        source.setStatus(AddOnStatus.ACTIVE);
        source.setLineageId(lineageId);
        AddOn draft = new AddOn();
        draft.setStatus(AddOnStatus.DRAFT);
        draft.setLineageId(lineageId);
        draft.setRevisionNumber(2);
        when(addOnRepository.findByIdForUpdate(sourceId)).thenReturn(Optional.of(source));
        when(addOnRepository.findLineageForUpdate(lineageId)).thenReturn(List.of(source, draft));

        assertThatThrownBy(() -> planAdminService.reviseAddOn(sourceId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("AddOn revision R2 is already an editable draft.");
        verify(addOnRepository, never()).saveAndFlush(any(AddOn.class));
    }

    @Test
    void addOnActivationRequiresOptionalFeatureModeOnAllowedPlan() {
        UUID addOnId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID lineageId = UUID.randomUUID();
        AddOn addOn = new AddOn();
        ReflectionTestUtils.setField(addOn, "id", addOnId);
        addOn.setCode("REPORTING_MODULE");
        addOn.setName("Reporting");
        addOn.setMoney(Money.of(BigDecimal.TEN, "USD"));
        addOn.setBillingCycle(BillingCycle.MONTHLY);
        addOn.setAllowedPlanCodes(Set.of("PRO"));
        addOn.setLineageId(lineageId);
        addOn.setRevisionNumber(2);
        AddOn previousRevision = new AddOn();
        ReflectionTestUtils.setField(previousRevision, "id", UUID.randomUUID());
        previousRevision.setLineageId(lineageId);
        previousRevision.setStatus(AddOnStatus.ACTIVE);
        Plan plan = plan(planId, "PRO");
        plan.setBillingCycle(BillingCycle.MONTHLY);
        Feature feature = feature("platform.company");
        AddOnFeature addOnFeature = new AddOnFeature();
        addOnFeature.setAddOn(addOn);
        addOnFeature.setFeature(feature);
        PlanFeature optional = planFeature(plan, feature, List.of());
        optional.setMode(PlanFeatureMode.OPTIONAL_ADD_ON);

        when(addOnRepository.findById(addOnId)).thenReturn(Optional.of(addOn));
        when(addOnFeatureRepository.findAllByAddOnId(addOnId)).thenReturn(List.of(addOnFeature));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(plan));
        when(planFeatureRepository.findAllByPlanId(planId)).thenReturn(List.of(optional));
        when(addOnRepository.findLineageForUpdate(lineageId)).thenReturn(List.of(previousRevision, addOn));
        when(addOnRepository.saveAndFlush(addOn)).thenReturn(addOn);
        when(addOnRepository.findDetailedById(addOnId)).thenReturn(Optional.of(addOn));

        AddOnDto activated = planAdminService.transitionAddOnStatus(addOnId, AddOnStatus.ACTIVE);

        assertThat(activated.status()).isEqualTo(AddOnStatus.ACTIVE);
        assertThat(previousRevision.getStatus()).isEqualTo(AddOnStatus.INACTIVE);
        verify(billingConfigurationValidator).validateAddOnFeature(
                "platform.company", List.of(), "USD");
    }

    @Test
    void deletePlanRemovesUnusedPlanAndItsFeatureRows() {
        UUID planId = UUID.randomUUID();
        Plan plan = plan(planId, "DRAFT");
        plan.setStatus(PlanStatus.DRAFT);
        PlanFeature planFeature = planFeature(plan, feature("platform.workspace"), List.of());

        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(planRepository.findByIdForUpdate(planId)).thenReturn(Optional.of(plan));
        when(planFeatureRepository.findAllByPlanId(planId)).thenReturn(List.of(planFeature));

        var preview = planAdminService.previewPlanDeletion(planId);
        planAdminService.deletePlan(planId, new DeletePlanRequest(
                plan.getCode(), preview.expectedVersion(), preview.previewToken()));

        verify(planFeatureRepository).deleteAll(List.of(planFeature));
        verify(planRepository).delete(plan);
    }

    private static Plan plan(UUID id) {
        return plan(id, "PRO");
    }

    private static Plan plan(UUID id, String code) {
        Plan plan = new Plan();
        ReflectionTestUtils.setField(plan, "id", id);
        plan.setCode(code);
        plan.setName(code);
        plan.setMoney(Money.zero("USD"));
        plan.setStatus(PlanStatus.ACTIVE);
        return plan;
    }

    private static Feature feature(String code) {
        Feature feature = new Feature();
        feature.setCode(code);
        feature.setStatus(FeatureStatus.PUBLIC);
        feature.setNewSalesEnabled(true);
        return feature;
    }

    private static PlanFeature planFeature(Plan plan, Feature feature, List<QuotaLimitEntry> quotas) {
        PlanFeature planFeature = new PlanFeature();
        planFeature.setPlan(plan);
        planFeature.setFeature(feature);
        planFeature.setMode(PlanFeatureMode.INCLUDED);
        planFeature.setQuotaConfigs(new ArrayList<>(quotas));
        return planFeature;
    }

    private void lenientSavedPlan() {
        org.mockito.Mockito.lenient()
                .when(planRepository.saveAndFlush(any(Plan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.lenient()
                .when(planRepository.save(any(Plan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<PlanFeature> capturedInheritedFeatures() {
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(planFeatureRepository).saveAll(captor.capture());
        return (List<PlanFeature>) captor.getValue();
    }
}
