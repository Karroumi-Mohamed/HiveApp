package com.hiveapp.platform.registry.service;

import org.springframework.stereotype.Component;
import com.hiveapp.platform.registry.definition.FeatureDefinition;

import java.util.Map;
import java.util.Set;

@Component
public class CurrentRegistrySnapshot {

    private volatile View snapshot = new View(Map.of(), Set.of(), null);

    public void install(RegistrySnapshot snapshot) {
        java.util.Objects.requireNonNull(snapshot, "Registry snapshot is required");
        // Build the index before publishing. Readers see definitions and actions from one
        // complete validated build; operational flags and user grants are never cached here.
        this.snapshot = new View(snapshot.definitionsByCode(), snapshot.actionCodes(), snapshot.hash());
    }

    public View view() {
        return snapshot;
    }

    public boolean containsAction(String permissionCode) {
        return permissionCode != null && snapshot.actionCodes().contains(permissionCode);
    }

    public String hash() {
        return snapshot.hash();
    }

    public Set<String> actionCodes() {
        return snapshot.actionCodes();
    }

    public record View(Map<String, FeatureDefinition> definitionsByCode, Set<String> actionCodes, String hash) {
        public View {
            definitionsByCode = Map.copyOf(definitionsByCode);
            actionCodes = Set.copyOf(actionCodes);
        }

        public FeatureDefinition definitionFor(String permissionCode) {
            if (permissionCode == null || !actionCodes.contains(permissionCode)) return null;
            int lastDot = permissionCode.lastIndexOf('.');
            if (lastDot < 1) return null;
            FeatureDefinition definition = definitionsByCode.get(permissionCode.substring(0, lastDot));
            return definition != null && definition.ownsPermission(permissionCode) ? definition : null;
        }
    }
}
