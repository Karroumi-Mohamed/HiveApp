package com.hiveapp.platform.registry.definition;

import com.hiveapp.platform.registry.domain.entity.Permission;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.platform.registry.service.CurrentRegistrySnapshot;
import com.hiveapp.shared.exception.InvalidPermissionGrantException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
public class PermissionGrantValidator {

    private final CurrentRegistrySnapshot currentRegistrySnapshot;
    private final FeatureRepository featureRepository;

    public PermissionGrantValidator(
            CurrentRegistrySnapshot currentRegistrySnapshot,
            FeatureRepository featureRepository) {
        this.currentRegistrySnapshot = currentRegistrySnapshot;
        this.featureRepository = featureRepository;
    }

    public void requireClientRoleGrantable(Permission permission) {
        requireFlag(permission.getCode(), GrantTarget.CLIENT_ROLE);
    }

    public void requireB2bDelegatable(Permission permission) {
        requireFlag(permission.getCode(), GrantTarget.B2B_DELEGATION);
    }

    public void requirePlatformAdminRoleGrantable(String permissionCode) {
        requireFlag(permissionCode, GrantTarget.PLATFORM_ADMIN_ROLE);
    }

    public boolean isPlatformAdminRoleGrantable(String permissionCode) {
        return isGrantable(permissionCode, GrantTarget.PLATFORM_ADMIN_ROLE);
    }

    /** A fresh evaluation, not a cross-request cache of mutable feature controls or grants. */
    public Set<String> platformAdminRoleGrantableCodes(Collection<String> permissionCodes) {
        CurrentRegistrySnapshot.View snapshot = currentRegistrySnapshot.view();
        Map<String, String> candidates = new HashMap<>();
        for (String code : permissionCodes) {
            FeatureDefinition definition = snapshot.definitionFor(code);
            if (definition != null && definition.isPlatformAdminRoleGrantablePermission(code)) {
                candidates.put(code, definition.code());
            }
        }
        if (candidates.isEmpty()) return Set.of();

        Set<String> availableFeatures = new HashSet<>();
        for (var controls : featureRepository.findGrantControlsByCodeIn(new HashSet<>(candidates.values()))) {
            if (controls.getNewGrantsEnabled() && controls.getRuntimeEnabled()) {
                availableFeatures.add(controls.getCode());
            }
        }
        Set<String> allowed = new HashSet<>();
        candidates.forEach((code, feature) -> {
            if (availableFeatures.contains(feature)) allowed.add(code);
        });
        return Set.copyOf(allowed);
    }

    public void requirePlatformAdminRoleGrantablePermissions(Collection<String> permissionCodes) {
        Set<String> allowed = platformAdminRoleGrantableCodes(permissionCodes);
        for (String code : permissionCodes) {
            if (code == null || !allowed.contains(code)) throw invalidGrant(code, GrantTarget.PLATFORM_ADMIN_ROLE);
        }
    }

    public boolean isClientRoleGrantable(Permission permission) {
        return isGrantable(permission.getCode(), GrantTarget.CLIENT_ROLE);
    }

    public boolean isOwnerUsable(Permission permission) {
        FeatureDefinition definition = findDefinition(permission.getCode());
        return definition != null && definition.surface() == FeatureSurface.CLIENT_WORKSPACE
                && featureAvailableForUse(definition.code());
    }

    public boolean isClientRoleRuntimeEligible(String permissionCode) {
        return isRuntimeEligible(permissionCode, GrantTarget.CLIENT_ROLE);
    }

    public boolean isB2bRuntimeEligible(String permissionCode) {
        return isRuntimeEligible(permissionCode, GrantTarget.B2B_DELEGATION);
    }

    private void requireFlag(String permissionCode, GrantTarget target) {
        if (!isGrantable(permissionCode, target)) {
            throw invalidGrant(permissionCode, target);
        }
    }

    private InvalidPermissionGrantException invalidGrant(String code, GrantTarget target) {
        return new InvalidPermissionGrantException("Permission " + code + " cannot be granted to " + target.label + ".");
    }

    private boolean isGrantable(String permissionCode, GrantTarget target) {
        FeatureDefinition definition = findDefinition(permissionCode);
        return definition != null && featureAvailableForNewGrant(definition.code()) && switch (target) {
            case CLIENT_ROLE -> definition.isClientRoleGrantablePermission(permissionCode);
            case PLATFORM_ADMIN_ROLE -> definition.isPlatformAdminRoleGrantablePermission(permissionCode);
            case B2B_DELEGATION -> definition.isB2bDelegatablePermission(permissionCode);
        };
    }

    private boolean featureAvailableForNewGrant(String featureCode) {
        return featureRepository.findByCode(featureCode)
                .map(feature -> feature.isNewGrantsEnabled() && feature.isRuntimeEnabled())
                .orElse(false);
    }

    private boolean featureAvailableForUse(String featureCode) {
        return featureRepository.findByCode(featureCode)
                .map(feature -> feature.isRuntimeEnabled())
                .orElse(false);
    }

    private boolean isRuntimeEligible(String permissionCode, GrantTarget target) {
        FeatureDefinition definition = findDefinition(permissionCode);
        if (definition == null || !featureAvailableForUse(definition.code())) {
            return false;
        }
        return switch (target) {
            case CLIENT_ROLE -> definition.isClientRoleGrantablePermission(permissionCode);
            case PLATFORM_ADMIN_ROLE -> definition.isPlatformAdminRoleGrantablePermission(permissionCode);
            case B2B_DELEGATION -> definition.isB2bDelegatablePermission(permissionCode);
        };
    }

    private FeatureDefinition findDefinition(String permissionCode) {
        return currentRegistrySnapshot.view().definitionFor(permissionCode);
    }

    private enum GrantTarget {
        CLIENT_ROLE("a client role"),
        PLATFORM_ADMIN_ROLE("a platform admin role"),
        B2B_DELEGATION("a B2B collaboration");

        private final String label;

        GrantTarget(String label) {
            this.label = label;
        }
    }
}
