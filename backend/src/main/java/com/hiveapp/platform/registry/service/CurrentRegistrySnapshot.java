package com.hiveapp.platform.registry.service;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class CurrentRegistrySnapshot {

    private volatile RegistrySnapshot snapshot;

    public void install(RegistrySnapshot snapshot) {
        this.snapshot = java.util.Objects.requireNonNull(snapshot, "Registry snapshot is required");
    }

    public boolean containsAction(String permissionCode) {
        RegistrySnapshot current = snapshot;
        return current != null && current.actionCodes().contains(permissionCode);
    }

    public String hash() {
        RegistrySnapshot current = snapshot;
        return current == null ? null : current.hash();
    }

    public Set<String> actionCodes() {
        RegistrySnapshot current = snapshot;
        return current == null ? Set.of() : current.actionCodes();
    }
}
