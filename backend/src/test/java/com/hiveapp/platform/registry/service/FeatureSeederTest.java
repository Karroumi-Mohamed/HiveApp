package com.hiveapp.platform.registry.service;

import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.FeatureDefinitionCollector;
import com.hiveapp.platform.registry.definition.WorkspaceFeature;
import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.entity.Module;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.platform.registry.domain.repository.ModuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeatureSeederTest {

    @Mock private ModuleRepository moduleRepository;
    @Mock private FeatureRepository featureRepository;

    @Test
    void newPublicCatalogFeaturesAreInitiallyPublicWhileControlFeaturesRemainInternal() {
        FeatureDefinition registry = FeatureDefinition.platformControl("platform.registry")
                .displayName("Registry")
                .build();
        FeatureSeeder seeder = seeder(List.of(WorkspaceFeature.definition(), registry));
        Module module = new Module();
        module.setCode("platform");
        when(moduleRepository.findByCode("platform")).thenReturn(Optional.of(module));
        when(featureRepository.findByCode(WorkspaceFeature.CODE)).thenReturn(Optional.empty());
        when(featureRepository.findByCode("platform.registry")).thenReturn(Optional.empty());

        seeder.seedFeatures();

        ArgumentCaptor<Feature> featureCaptor = ArgumentCaptor.forClass(Feature.class);
        org.mockito.Mockito.verify(featureRepository, org.mockito.Mockito.times(2)).save(featureCaptor.capture());
        assertThat(featureCaptor.getAllValues())
                .filteredOn(feature -> WorkspaceFeature.CODE.equals(feature.getCode()))
                .extracting(Feature::getStatus)
                .containsExactly(FeatureStatus.PUBLIC);
        assertThat(featureCaptor.getAllValues())
                .filteredOn(feature -> "platform.registry".equals(feature.getCode()))
                .extracting(Feature::getStatus)
                .containsExactly(FeatureStatus.INTERNAL);
    }

    @Test
    void existingLifecycleStatusIsOverwrittenFromCodeButActivationIsPreserved() {
        Feature existing = new Feature();
        existing.setCode(WorkspaceFeature.CODE);
        existing.setStatus(FeatureStatus.DEPRECATED);
        existing.setActive(false);
        Module module = new Module();
        module.setCode("platform");
        when(moduleRepository.findByCode("platform")).thenReturn(Optional.of(module));
        when(featureRepository.findByCode(WorkspaceFeature.CODE)).thenReturn(Optional.of(existing));

        seeder(List.of(WorkspaceFeature.definition())).seedFeatures();

        assertThat(existing.getStatus()).isEqualTo(FeatureStatus.PUBLIC);
        assertThat(existing.isActive()).isFalse();
    }

    private FeatureSeeder seeder(List<FeatureDefinition> definitions) {
        FeatureDefinitionCollector collector = new FeatureDefinitionCollector(
                List.of(() -> definitions));
        return new FeatureSeeder(moduleRepository, featureRepository, collector);
    }
}
