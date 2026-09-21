package com.hiveapp.platform.client.plan.infrastructure;

import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import com.hiveapp.platform.client.plan.domain.repository.PlanFeatureRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanSeederTest {

    private static final List<String> PLAN_CODES =
            List.of("FREE", "FLEX", "PRO", "BUSINESS", "SCALE", "ENTERPRISE");

    @Mock private PlanRepository planRepository;
    @Mock private PlanFeatureRepository planFeatureRepository;
    @Mock private FeatureRepository featureRepository;

    private PlanSeeder planSeeder;

    @BeforeEach
    void setUp() {
        planSeeder = new PlanSeeder(planRepository, planFeatureRepository, featureRepository);
        for (String featureCode : PlanSeeder.BASELINE_FEATURE_CODES) {
            Feature feature = new Feature();
            feature.setCode(featureCode);
            org.mockito.Mockito.lenient()
                    .when(featureRepository.findByCode(featureCode))
                    .thenReturn(Optional.of(feature));
        }
        org.mockito.Mockito.lenient()
                .when(planRepository.save(any(Plan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsMissingBootstrapPlansWithOnlyTheExplicitBaselineComposition() {
        when(planRepository.findByCode(any())).thenReturn(Optional.empty());

        planSeeder.seed();

        ArgumentCaptor<Plan> plans = ArgumentCaptor.forClass(Plan.class);
        verify(planRepository, times(PLAN_CODES.size())).save(plans.capture());
        assertThat(plans.getAllValues()).extracting(Plan::getCode)
                .containsExactlyElementsOf(PLAN_CODES);
        assertThat(plans.getAllValues()).extracting(Plan::getCurrencyCode)
                .containsOnly("MAD");

        ArgumentCaptor<PlanFeature> mappings = ArgumentCaptor.forClass(PlanFeature.class);
        verify(planFeatureRepository, times(PLAN_CODES.size() * PlanSeeder.BASELINE_FEATURE_CODES.size()))
                .save(mappings.capture());
        assertThat(mappings.getAllValues())
                .allSatisfy(mapping -> assertThat(PlanSeeder.BASELINE_FEATURE_CODES)
                        .contains(mapping.getFeature().getCode()));
        assertThat(mappings.getAllValues())
                .filteredOn(mapping -> "platform.workspace".equals(mapping.getFeature().getCode()))
                .hasSize(PLAN_CODES.size());

        assertThat(mappings.getAllValues())
                .filteredOn(mapping -> "FLEX".equals(mapping.getPlan().getCode()))
                .filteredOn(mapping -> Set.of("platform.organization", "platform.rbac", "platform.b2b")
                        .contains(mapping.getFeature().getCode()))
                .allSatisfy(mapping -> assertThat(mapping.getMode())
                        .isEqualTo(com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode.OPTIONAL_ADD_ON));
        assertThat(mappings.getAllValues())
                .filteredOn(mapping -> "FREE".equals(mapping.getPlan().getCode()))
                .filteredOn(mapping -> Set.of("platform.organization", "platform.rbac", "platform.b2b")
                        .contains(mapping.getFeature().getCode()))
                .allSatisfy(mapping -> assertThat(mapping.getMode())
                        .isEqualTo(com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode.INCLUDED));
    }

    @Test
    void repeatedSeedPreservesExistingPlansAndComposition() {
        for (String code : PLAN_CODES) {
            when(planRepository.findByCode(code)).thenReturn(Optional.of(plan(code, true)));
        }

        planSeeder.seed();

        verify(planRepository, never()).save(any(Plan.class));
        verify(planFeatureRepository, never()).save(any(PlanFeature.class));
    }

    @Test
    void createsMissingDefaultsEvenWhenOtherPlanRowsAlreadyExist() {
        when(planRepository.findByCode("FREE")).thenReturn(Optional.empty());
        for (String code : PLAN_CODES) {
            if (!"FREE".equals(code)) {
                when(planRepository.findByCode(code)).thenReturn(Optional.of(plan(code, true)));
            }
        }

        planSeeder.seed();

        verify(planRepository).save(any(Plan.class));
        verify(planFeatureRepository, times(PlanSeeder.BASELINE_FEATURE_CODES.size())).save(any(PlanFeature.class));
    }

    @Test
    void rejectsAnInactiveExistingDefaultPlan() {
        when(planRepository.findByCode("FREE")).thenReturn(Optional.of(plan("FREE", false)));

        assertThatThrownBy(planSeeder::seed)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The required FREE provisioning plan exists but is inactive.");

        verify(planRepository, never()).save(any(Plan.class));
        verify(planFeatureRepository, never()).save(any(PlanFeature.class));
    }

    @Test
    void validatesTheWholeExplicitFeatureBaselineBeforeWritingPlans() {
        String missingCode = PlanSeeder.BASELINE_FEATURE_CODES.getLast();
        when(featureRepository.findByCode(missingCode)).thenReturn(Optional.empty());

        assertThatThrownBy(planSeeder::seed)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(missingCode);

        verify(planRepository, never()).save(any(Plan.class));
        verify(planFeatureRepository, never()).save(any(PlanFeature.class));
    }

    private Plan plan(String code, boolean active) {
        Plan plan = new Plan();
        plan.setCode(code);
        plan.setStatus(active ? PlanStatus.ACTIVE : PlanStatus.INACTIVE);
        return plan;
    }
}
