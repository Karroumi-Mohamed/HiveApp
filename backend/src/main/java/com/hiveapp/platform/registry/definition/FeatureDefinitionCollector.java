package com.hiveapp.platform.registry.definition;

import dev.karroumi.permissionizer.PermissionNode;
import dev.karroumi.permissionizer.PermissionResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
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

    public Set<String> guardedFeatureCodes() {
        validateContributorRoots(contributors);
        return contributors.stream()
                .filter(contributor -> AopUtils.getTargetClass(contributor)
                        .getAnnotation(PermissionNode.class) != null)
                .map(contributor -> contributor.featureDefinitions().get(0).code())
                .collect(Collectors.toUnmodifiableSet());
    }

    public Set<String> guardedActionCodes() {
        validateContributorRoots(contributors);
        return contributors.stream()
                .map(AopUtils::getTargetClass)
                .flatMap(type -> Arrays.stream(type.getMethods()))
                .filter(method -> AnnotationUtils.findAnnotation(method, PermissionNode.class) != null)
                .map(PermissionResolver::resolve)
                .filter(PermissionResolver.Result::shouldCheck)
                .map(PermissionResolver.Result::path)
                .collect(Collectors.toUnmodifiableSet());
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
