package com.hiveapp.shared.security.policy;

import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.platform.registry.service.CurrentRegistrySnapshot;
import dev.karroumi.permissionizer.Permission;
import dev.karroumi.permissionizer.PermissionPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Mandatory fail-closed precondition evaluated before every actor-specific policy. */
@Component
@RequiredArgsConstructor
public class FeatureRuntimePolicy implements PermissionPolicy {

    private final FeatureRepository featureRepository;
    private final CurrentRegistrySnapshot currentRegistrySnapshot;

    @Override
    public Decision evaluate(Permission requested, Object context) {
        String featureCode = featureCode(requested.path());
        if (featureCode == null) {
            return Decision.DENIED;
        }
        boolean featureRoot = featureCode.equals(requested.path());
        if (!featureRoot && !currentRegistrySnapshot.containsAction(requested.path())) {
            return Decision.DENIED;
        }
        return featureRepository.findByCode(featureCode)
                .filter(feature -> feature.isRuntimeEnabled())
                .map(feature -> Decision.ABSTAIN)
                .orElse(Decision.DENIED);
    }

    private String featureCode(String permissionCode) {
        if (permissionCode == null) {
            return null;
        }
        int firstDot = permissionCode.indexOf('.');
        int lastDot = permissionCode.lastIndexOf('.');
        if (firstDot < 1) {
            return null;
        }
        return firstDot == lastDot ? permissionCode : permissionCode.substring(0, lastDot);
    }
}
