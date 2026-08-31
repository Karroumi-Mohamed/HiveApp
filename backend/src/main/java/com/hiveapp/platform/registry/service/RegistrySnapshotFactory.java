package com.hiveapp.platform.registry.service;

import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.FeatureDefinitionCollector;
import com.hiveapp.platform.registry.definition.FeatureDefinitionException;
import dev.karroumi.permissionizer.CollectedPermission;
import dev.karroumi.permissionizer.PermissionCollector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RegistrySnapshotFactory {

    private final FeatureDefinitionCollector featureDefinitionCollector;

    public RegistrySnapshot discoverAndValidate() {
        return validate(
                featureDefinitionCollector.collect(),
                featureDefinitionCollector.guardedFeatureCodes(),
                featureDefinitionCollector.guardedActionCodes(),
                PermissionCollector.collect());
    }

    RegistrySnapshot validate(
            List<FeatureDefinition> definitions,
            Set<String> guardedFeatureCodes,
            List<CollectedPermission> collectedPermissions) {
        return validate(definitions, guardedFeatureCodes, Set.of(), collectedPermissions);
    }

    RegistrySnapshot validate(
            List<FeatureDefinition> definitions,
            Set<String> guardedFeatureCodes,
            Set<String> expectedActionCodes,
            List<CollectedPermission> collectedPermissions) {
        if (definitions == null || definitions.isEmpty()) {
            throw invalid("Registry discovery returned no feature definitions");
        }
        if (collectedPermissions == null || collectedPermissions.isEmpty()) {
            throw invalid("Permissionizer discovery returned no permissions");
        }

        Map<String, FeatureDefinition> definitionsByCode = definitions.stream()
                .collect(Collectors.toUnmodifiableMap(FeatureDefinition::code, definition -> definition));
        List<CollectedPermission> actions = collectedPermissions.stream()
                .filter(permission -> !PermissionSeeder.isStructuralNode(permission.path()))
                .toList();
        if (actions.isEmpty()) {
            throw invalid("Permissionizer discovery returned no concrete action permissions");
        }

        Set<String> actionCodes = new HashSet<>();
        Set<String> featuresWithActions = new HashSet<>();
        for (CollectedPermission permission : actions) {
            if (!PermissionSeeder.isStrictActionPermission(permission.path())) {
                throw invalid("Permission '" + permission.path()
                        + "' does not match required shape '<module>.<feature>.<action>'");
            }
            if (!actionCodes.add(permission.path())) {
                throw invalid("Duplicate permission action discovered: " + permission.path());
            }
            String featureCode = PermissionSeeder.extractFeatureCode(permission.path());
            FeatureDefinition definition = definitionsByCode.get(featureCode);
            if (definition == null) {
                throw invalid("Permission '" + permission.path()
                        + "' has no matching FeatureDefinition '" + featureCode + "'");
            }
            if (!PermissionSeeder.extractModuleCode(permission.path()).equals(definition.moduleCode())) {
                throw invalid("Permission '" + permission.path()
                        + "' does not match its FeatureDefinition module");
            }
            featuresWithActions.add(featureCode);
        }

        Set<String> missingGuardedFeatures = guardedFeatureCodes.stream()
                .filter(code -> !featuresWithActions.contains(code))
                .collect(Collectors.toCollection(java.util.TreeSet::new));
        if (!missingGuardedFeatures.isEmpty()) {
            throw invalid("Guarded features have no discovered actions: "
                    + String.join(", ", missingGuardedFeatures));
        }
        Set<String> missingActions = expectedActionCodes.stream()
                .filter(code -> !actionCodes.contains(code))
                .collect(Collectors.toCollection(java.util.TreeSet::new));
        Set<String> unexpectedActions = actionCodes.stream()
                .filter(code -> !expectedActionCodes.isEmpty() && !expectedActionCodes.contains(code))
                .collect(Collectors.toCollection(java.util.TreeSet::new));
        if (!missingActions.isEmpty() || !unexpectedActions.isEmpty()) {
            throw invalid("Permissionizer action collection does not match guarded methods; missing="
                    + missingActions + ", unexpected=" + unexpectedActions);
        }

        for (FeatureDefinition definition : definitions) {
            validateClassifiedActions(definition, definition.ownerOnlyActions(), "owner-only", actionCodes);
            validateClassifiedActions(
                    definition, definition.b2bDelegatableActions(), "B2B-delegatable", actionCodes);
            Set<String> conflicting = definition.ownerOnlyActions().stream()
                    .filter(definition.b2bDelegatableActions()::contains)
                    .collect(Collectors.toCollection(java.util.TreeSet::new));
            if (!conflicting.isEmpty()) {
                throw invalid("Feature '" + definition.code()
                        + "' classifies actions as both owner-only and B2B-delegatable: " + conflicting);
            }
        }

        List<FeatureDefinition> sortedDefinitions = definitions.stream()
                .sorted(java.util.Comparator.comparing(FeatureDefinition::code))
                .toList();
        List<CollectedPermission> sortedActions = actions.stream()
                .sorted(java.util.Comparator.comparing(CollectedPermission::path))
                .toList();
        return new RegistrySnapshot(
                sortedDefinitions,
                sortedActions,
                actionCodes,
                hash(sortedDefinitions, sortedActions));
    }

    private String hash(
            List<FeatureDefinition> definitions,
            List<CollectedPermission> permissions) {
        StringBuilder canonical = new StringBuilder();
        definitions.forEach(definition -> {
            canonical.append("F");
            append(canonical, definition.code());
            append(canonical, definition.moduleCode());
            append(canonical, definition.featureKey());
            append(canonical, definition.displayName());
            append(canonical, definition.description());
            append(canonical, definition.surface());
            append(canonical, definition.lifecycleStatus());
            append(canonical, definition.planAssignable());
            append(canonical, definition.clientRoleGrantable());
            append(canonical, definition.platformAdminRoleGrantable());
            append(canonical, definition.b2bDelegatable());
            append(canonical, definition.publicCatalogVisible());
            append(canonical, definition.sortOrder());
            definition.quotaSlots().stream()
                    .sorted(java.util.Comparator.comparing(slot -> slot.resource()))
                    .forEach(slot -> append(canonical, slot));
            definition.ownerOnlyActions().stream()
                    .sorted()
                    .forEach(value -> append(canonical, value));
            canonical.append('|');
            definition.b2bDelegatableActions().stream()
                    .sorted()
                    .forEach(action -> append(canonical, action));
            canonical.append('\n');
        });
        permissions.forEach(permission -> {
            canonical.append("P");
            append(canonical, permission.path());
            append(canonical, permission.key());
            append(canonical, permission.description());
            canonical.append('\n');
        });
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void validateClassifiedActions(
            FeatureDefinition definition,
            Set<String> actions,
            String classification,
            Set<String> discoveredActionCodes) {
        Set<String> unknown = actions.stream()
                .map(action -> definition.code() + "." + action)
                .filter(code -> !discoveredActionCodes.contains(code))
                .collect(Collectors.toCollection(java.util.TreeSet::new));
        if (!unknown.isEmpty()) {
            throw invalid("Feature '" + definition.code() + "' declares unknown "
                    + classification + " actions: " + unknown);
        }
    }

    private void append(StringBuilder target, Object value) {
        String text = String.valueOf(value);
        target.append('|').append(text.length()).append(':').append(text);
    }

    private FeatureDefinitionException invalid(String message) {
        return new FeatureDefinitionException(message);
    }
}
