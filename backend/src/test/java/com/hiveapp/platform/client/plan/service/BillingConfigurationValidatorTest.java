package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.registry.definition.CompanyFeature;
import com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode;
import com.hiveapp.platform.registry.definition.FeatureDefinitionCollector;
import com.hiveapp.platform.registry.definition.PlansFeature;
import com.hiveapp.platform.registry.definition.WorkspaceFeature;
import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingConfigurationValidatorTest {

    @Mock private FeatureRepository featureRepository;

    private BillingConfigurationValidator validator;

    @BeforeEach
    void setUp() {
        validator = new BillingConfigurationValidator(featureRepository, provider(new FeatureDefinitionCollector(List.of(
                () -> List.of(WorkspaceFeature.definition(), CompanyFeature.definition(), PlansFeature.definition())
        ))));
    }

    @Test
    void rejectsPlatformControlFeatureForPlanAssignment() {
        assertThatThrownBy(() -> validator.validatePlanFeature(
                "platform.plans", PlanFeatureMode.INCLUDED, List.of(), "USD"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("cannot be assigned");
    }

    @Test
    void rejectsUnknownQuotaResourceForPlanAssignment() {
        when(featureRepository.findByCode(WorkspaceFeature.CODE)).thenReturn(Optional.of(feature(WorkspaceFeature.CODE)));

        assertThatThrownBy(() -> validator.validatePlanFeature(
                WorkspaceFeature.CODE, PlanFeatureMode.INCLUDED,
                List.of(new QuotaLimitEntry("projects", 5L)), "USD"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("not declared");
    }

    @Test
    void rejectsQuotaConfigurationForFeatureWithoutQuotaSlots() {
        when(featureRepository.findByCode(CompanyFeature.CODE)).thenReturn(Optional.of(feature(CompanyFeature.CODE)));

        assertThatThrownBy(() -> validator.validatePlanFeature(
                CompanyFeature.CODE, PlanFeatureMode.INCLUDED,
                List.of(new QuotaLimitEntry("companies", 1L)), "USD"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("not declared");
    }

    @Test
    void rejectsUnknownQuotaResourceForPackageDefinition() {
        when(featureRepository.findByCode(WorkspaceFeature.CODE)).thenReturn(Optional.of(feature(WorkspaceFeature.CODE)));

        assertThatThrownBy(() -> validator.validateQuotaPackageDefinition(
                WorkspaceFeature.CODE, "projects"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("not declared");
    }

    @Test
    void acceptsDeclaredQuotaPackageDefinition() {
        when(featureRepository.findByCode(WorkspaceFeature.CODE)).thenReturn(Optional.of(feature(WorkspaceFeature.CODE)));

        validator.validateQuotaPackageDefinition(WorkspaceFeature.CODE, WorkspaceFeature.MEMBERS);
    }

    private static Feature feature(String code) {
        Feature feature = new Feature();
        feature.setCode(code);
        feature.setStatus(FeatureStatus.PUBLIC);
        feature.setNewSalesEnabled(true);
        return feature;
    }

    private static ObjectProvider<FeatureDefinitionCollector> provider(FeatureDefinitionCollector collector) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("featureDefinitionCollector", collector);
        return beanFactory.getBeanProvider(FeatureDefinitionCollector.class);
    }
}
