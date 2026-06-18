package com.hiveapp.platform.registry.service;

import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.FeatureDefinitionCollector;
import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.platform.registry.dto.PublicFeatureCatalogFeatureDto;
import com.hiveapp.platform.registry.dto.PublicFeatureCatalogModuleDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PublicFeatureCatalogService {

    private final ObjectProvider<FeatureDefinitionCollector> featureDefinitionCollectorProvider;
    private final FeatureRepository featureRepository;

    @Transactional(readOnly = true)
    public List<PublicFeatureCatalogModuleDto> getPublicCatalog() {
        Map<String, Feature> featuresByCode = featureRepository.findAll().stream()
                .collect(Collectors.toMap(Feature::getCode, Function.identity()));

        return featureDefinitionCollectorProvider.getObject().collect().stream()
                .filter(FeatureDefinition::publicCatalogVisible)
                .filter(definition -> isPubliclyVisible(featuresByCode.get(definition.code())))
                .sorted(Comparator.comparing(FeatureDefinition::moduleCode)
                        .thenComparing(FeatureDefinition::sortOrder)
                        .thenComparing(FeatureDefinition::code))
                .collect(Collectors.groupingBy(
                        FeatureDefinition::moduleCode,
                        java.util.LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet().stream()
                .map(entry -> new PublicFeatureCatalogModuleDto(
                        entry.getKey(),
                        entry.getValue().stream()
                                .map(definition -> toFeatureDto(definition, featuresByCode.get(definition.code())))
                                .toList()))
                .filter(module -> !module.features().isEmpty())
                .toList();
    }

    private boolean isPubliclyVisible(Feature feature) {
        return feature != null
                && feature.isActive()
                && (feature.getStatus() == FeatureStatus.PUBLIC || feature.getStatus() == FeatureStatus.BETA);
    }

    private PublicFeatureCatalogFeatureDto toFeatureDto(FeatureDefinition definition, Feature feature) {
        return new PublicFeatureCatalogFeatureDto(
                definition.code(),
                definition.displayName(),
                definition.description(),
                feature.getStatus(),
                definition.quotaSlots()
        );
    }
}
