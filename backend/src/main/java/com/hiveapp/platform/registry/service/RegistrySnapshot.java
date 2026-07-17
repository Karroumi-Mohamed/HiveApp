package com.hiveapp.platform.registry.service;

import com.hiveapp.platform.registry.definition.FeatureDefinition;
import dev.karroumi.permissionizer.CollectedPermission;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public record RegistrySnapshot(
        List<FeatureDefinition> featureDefinitions,
        List<CollectedPermission> actionPermissions,
        Set<String> actionCodes,
        String hash
) {
    public RegistrySnapshot {
        featureDefinitions = List.copyOf(featureDefinitions);
        actionPermissions = List.copyOf(actionPermissions);
        actionCodes = Set.copyOf(actionCodes);
    }

    public Map<String, FeatureDefinition> definitionsByCode() {
        return featureDefinitions.stream()
                .collect(Collectors.toUnmodifiableMap(FeatureDefinition::code, Function.identity()));
    }
}
