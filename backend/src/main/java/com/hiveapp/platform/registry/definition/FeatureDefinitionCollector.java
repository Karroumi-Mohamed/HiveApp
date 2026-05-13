package com.hiveapp.platform.registry.definition;

import dev.karroumi.permissionizer.PermissionNode;
import dev.karroumi.permissionizer.PermissionResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FeatureDefinitionCollector {

    private final List<FeatureContributor> contributors;

    public List<FeatureDefinition> collect() {
        validateContributorRoots(contributors);

        List<FeatureDefinition> definitions = contributors.stream()
                .flatMap(contributor -> contributor.featureDefinitions().stream())
                .sorted(Comparator.comparing(FeatureDefinition::moduleCode)
                        .thenComparing(FeatureDefinition::sortOrder)
                        .thenComparing(FeatureDefinition::code))
                .toList();

        validateUniqueCodes(definitions);
        return definitions;
    }

    public Map<String, FeatureDefinition> collectByCode() {
        return collect().stream()
                .collect(Collectors.toUnmodifiableMap(FeatureDefinition::code, Function.identity()));
    }

    private void validateUniqueCodes(List<FeatureDefinition> definitions) {
        Map<String, Long> counts = definitions.stream()
                .collect(Collectors.groupingBy(FeatureDefinition::code, Collectors.counting()));

        counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .findFirst()
                .ifPresent(entry -> {
                    throw new FeatureDefinitionException("Duplicate feature definition: " + entry.getKey());
                });
    }

    private void validateContributorRoots(List<FeatureContributor> contributors) {
        for (FeatureContributor contributor : contributors) {
            Class<?> contributorClass = AopUtils.getTargetClass(contributor);
            PermissionNode root = contributorClass.getAnnotation(PermissionNode.class);
            if (root == null) {
                continue;
            }

            List<FeatureDefinition> definitions = contributor.featureDefinitions();
            if (definitions.size() != 1) {
                throw new FeatureDefinitionException(
                        "Guarded feature service " + contributorClass.getName()
                                + " must contribute exactly one feature definition.");
            }

            String resolvedRoot = PermissionResolver.resolveClassPath(contributorClass);
            String featureCode = definitions.get(0).code();
            if (!featureCode.equals(resolvedRoot)) {
                throw new FeatureDefinitionException(
                        "Feature definition " + featureCode
                                + " does not match Permissionizer root " + resolvedRoot
                                + " on " + contributorClass.getName());
            }
        }
    }
}
