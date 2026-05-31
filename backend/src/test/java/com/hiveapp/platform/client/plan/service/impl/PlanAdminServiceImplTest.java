package com.hiveapp.platform.client.plan.service.impl;

import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.dto.AssignPlanFeatureRequest;
import com.hiveapp.platform.client.plan.service.BillingConfigurationValidator;
import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.shared.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    @Mock private BillingConfigurationValidator billingConfigurationValidator;

    @InjectMocks
    private PlanAdminServiceImpl planAdminService;

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

    private static Plan plan(UUID id) {
        Plan plan = new Plan();
        ReflectionTestUtils.setField(plan, "id", id);
        plan.setCode("PRO");
        plan.setName("Pro");
        return plan;
    }

    private static Feature feature(String code) {
        Feature feature = new Feature();
        feature.setCode(code);
        feature.setStatus(FeatureStatus.PUBLIC);
        feature.setActive(true);
        return feature;
    }
}
