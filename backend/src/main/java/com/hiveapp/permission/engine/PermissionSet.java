package com.hiveapp.permission.engine;

import com.hiveapp.permission.domain.entity.Permission;
import lombok.Getter;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Immutable value object representing a calculated set of permissions.
 * Supports set operations for permission resolution.
 */
@Getter
public class PermissionSet implements Serializable {

    private final Map<String, Permission> permissions;

    private PermissionSet(Map<String, Permission> permissions) {
        this.permissions = Collections.unmodifiableMap(permissions);
    }

    public static PermissionSet of(Collection<Permission> permissions) {
        Map<String, Permission> map = permissions.stream()
                .collect(Collectors.toMap(
                        Permission::getCode,
                        p -> p,
                        (existing, replacement) -> existing
                ));
        return new PermissionSet(map);
    }

    public static PermissionSet empty() {
        return new PermissionSet(Collections.emptyMap());
    }

    /**
     * Intersection: returns permissions present in BOTH sets.
     * Used to apply ceilings (Plan ceiling, Collaboration ceiling).
     */
    public PermissionSet intersect(PermissionSet other) {
        Map<String, Permission> result = new HashMap<>();
        for (Map.Entry<String, Permission> entry : this.permissions.entrySet()) {
            if (other.permissions.containsKey(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return new PermissionSet(result);
    }

    /**
     * Union: returns permissions present in EITHER set.
     * Used to merge permissions from multiple roles.
     */
    public PermissionSet union(PermissionSet other) {
        Map<String, Permission> result = new HashMap<>(this.permissions);
        result.putAll(other.permissions);
        return new PermissionSet(result);
    }

    /**
     * Subtract: returns permissions in this set that are NOT in the other set.
     */
    public PermissionSet subtract(PermissionSet other) {
        Map<String, Permission> result = new HashMap<>();
        for (Map.Entry<String, Permission> entry : this.permissions.entrySet()) {
            if (!other.permissions.containsKey(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return new PermissionSet(result);
    }

    public boolean has(String permissionCode) {
        return permissions.containsKey(permissionCode);
    }

    public boolean hasAction(String action, String resource) {
        return permissions.values().stream()
                .anyMatch(p -> p.matches(action, resource));
    }

    public boolean isEmpty() {
        return permissions.isEmpty();
    }

    public int size() {
        return permissions.size();
    }

    public List<Permission> toList() {
        return new ArrayList<>(permissions.values());
    }

    public Set<String> getCodes() {
        return permissions.keySet();
    }

    public Set<UUID> getIds() {
        return permissions.values().stream()
                .map(Permission::getId)
                .collect(Collectors.toSet());
    }
}
