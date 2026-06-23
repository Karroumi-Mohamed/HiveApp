package com.hiveapp.platform.client.plan.service.impl;

import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.dto.AssignPlanFeatureRequest;
import com.hiveapp.platform.client.plan.dto.CreatePlanRequest;
import com.hiveapp.platform.client.plan.dto.UpdatePlanRequest;
import com.hiveapp.platform.client.plan.service.BillingConfigurationValidator;
import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.shared.exception.BusinessException;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    @Mock private BillingConfigurationValidator billingConfigurationValidator;

    @InjectMocks
    private PlanAdminServiceImpl planAdminService;

    @BeforeEach
    void setUp() {
        lenientSavedPlan();
    }

    @Test
    void createPlanDefaultsToFreePlanCompositionWhenNoSourceIsProvided() {
        UUID freePlanId = UUID.randomUUID();
        Plan freePlan = plan(freePlanId, "FREE");
        Feature workspace = feature("platform.workspace");
        PlanFeature sourceFeature = planFeature(freePlan, workspace,
                List.of(new QuotaLimitEntry("members", 3L, new BigDecimal("2.00"))));

        when(planRepository.findByCode("STARTER")).thenReturn(Optional.empty());
        when(planRepository.findByCode("FREE")).thenReturn(Optional.of(freePlan));
        when(planFeatureRepository.findAllByPlanId(freePlanId)).thenReturn(List.of(sourceFeature));

        planAdminService.createPlan(new CreatePlanRequest(
                "STARTER",
                "Starter",
                null,
                BigDecimal.TEN,
                BillingCycle.MONTHLY,
                null
        ));

        var inheritedFeatures = capturedInheritedFeatures();
        assertThat(inheritedFeatures).hasSize(1);
        assertThat(inheritedFeatures.getFirst().getFeature()).isSameAs(workspace);
        assertThat(inheritedFeatures.getFirst().getAddOnPrice()).isNull();
        assertThat(inheritedFeatures.getFirst().getQuotaConfigs())
                .containsExactly(new QuotaLimitEntry("members", 3L, new BigDecimal("2.00")));
        assertThat(inheritedFeatures.getFirst().getQuotaConfigs()).isNotSameAs(sourceFeature.getQuotaConfigs());
        verify(billingConfigurationValidator).validatePlanFeature(
                "platform.workspace",
                null,
                sourceFeature.getQuotaConfigs());
    }

    @Test
    void createPlanCanInheritFromExplicitSourcePlan() {
        UUID sourcePlanId = UUID.randomUUID();
        Plan sourcePlan = plan(sourcePlanId, "PRO");

        when(planRepository.findByCode("TEAM")).thenReturn(Optional.empty());
        when(planRepository.findById(sourcePlanId)).thenReturn(Optional.of(sourcePlan));
        when(planFeatureRepository.findAllByPlanId(sourcePlanId)).thenReturn(List.of());

        planAdminService.createPlan(new CreatePlanRequest(
                "TEAM",
                "Team",
                null,
                new BigDecimal("49.00"),
                BillingCycle.MONTHLY,
                sourcePlanId
        ));

        verify(planRepository).findById(sourcePlanId);
        verify(planRepository, never()).findByCode("FREE");
        verify(planFeatureRepository, never()).saveAll(any());
    }

    @Test
    void assignFeatureRejectsConfigurationRejectedByBillingValidator() {
        UUID planId = UUID.randomUUID();
        String featureCode = "platform.plans";

        when(planRepository.findById(planId)).thenReturn(Optional.of(plan(planId)));
        doThrow(new InvalidRequestException("Feature cannot be assigned to billing configuration."))
                .when(billingConfigurationValidator).validatePlanFeature(featureCode, null, List.of());

        assertThatThrownBy(() -> planAdminService.assignFeature(
                planId,
                new AssignPlanFeatureRequest(featureCode, null, List.of())
        ))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("billing configuration");

        verify(planFeatureRepository, never()).save(any(PlanFeature.class));
    }

    @Test
    void updateFeatureAppliesBillingValidationBeforeSavingQuotaConfiguration() {
        UUID planId = UUID.randomUUID();
        UUID planFeatureId = UUID.randomUUID();
        Feature feature = feature("platform.workspace");
        PlanFeature planFeature = new PlanFeature();
        planFeature.setPlan(plan(planId));
        planFeature.setFeature(feature);
        var request = new AssignPlanFeatureRequest("platform.workspace", null, List.of());

        when(planFeatureRepository.findById(planFeatureId)).thenReturn(Optional.of(planFeature));
        when(planFeatureRepository.save(planFeature)).thenReturn(planFeature);

        planAdminService.updateFeature(planId, planFeatureId, request);

        verify(billingConfigurationValidator).validatePlanFeature("platform.workspace", null, List.of());
        verify(planFeatureRepository).save(planFeature);
    }

    @Test
    void updatePlanRejectsForeverBillingCycleForNonFreePlan() {
        UUID planId = UUID.randomUUID();
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan(planId, "PRO")));

        assertThatThrownBy(() -> planAdminService.updatePlan(
                planId,
                new UpdatePlanRequest("Pro", null, BigDecimal.TEN, BillingCycle.FOREVER)
        ))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("BillingCycle.FOREVER is reserved for the FREE plan.");

        verify(planRepository, never()).save(any(Plan.class));
    }

    @Test
    void deletePlanRejectsPlanWithSubscriptionHistory() {
        UUID planId = UUID.randomUUID();
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan(planId, "PRO")));
        when(subscriptionRepository.countByPlan_Id(planId)).thenReturn(2L);

        assertThatThrownBy(() -> planAdminService.deletePlan(planId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot be deleted");

        verify(planRepository, never()).delete(any(Plan.class));
    }

    @Test
    void deletePlanRemovesUnusedPlanAndItsFeatureRows() {
        UUID planId = UUID.randomUUID();
        Plan plan = plan(planId, "DRAFT");
        PlanFeature planFeature = planFeature(plan, feature("platform.workspace"), List.of());

        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.countByPlan_Id(planId)).thenReturn(0L);
        when(planFeatureRepository.findAllByPlanId(planId)).thenReturn(List.of(planFeature));

        planAdminService.deletePlan(planId);

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
        return plan;
    }

    private static Feature feature(String code) {
        Feature feature = new Feature();
        feature.setCode(code);
        feature.setStatus(FeatureStatus.PUBLIC);
        feature.setActive(true);
        return feature;
    }

    private static PlanFeature planFeature(Plan plan, Feature feature, List<QuotaLimitEntry> quotas) {
        PlanFeature planFeature = new PlanFeature();
        planFeature.setPlan(plan);
        planFeature.setFeature(feature);
        planFeature.setQuotaConfigs(new ArrayList<>(quotas));
        return planFeature;
    }

    private void lenientSavedPlan() {
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
