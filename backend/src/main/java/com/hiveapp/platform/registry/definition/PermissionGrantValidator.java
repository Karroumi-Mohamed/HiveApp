package com.hiveapp.platform.registry.definition;

import com.hiveapp.platform.registry.domain.entity.Permission;
import com.hiveapp.shared.exception.InvalidPermissionGrantException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PermissionGrantValidator {

    private final ObjectProvider<FeatureDefinitionCollector> featureDefinitionCollectorProvider;

    public PermissionGrantValidator(ObjectProvider<FeatureDefinitionCollector> featureDefinitionCollectorProvider) {
        this.featureDefinitionCollectorProvider = featureDefinitionCollectorProvider;
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

    public boolean isClientRoleGrantable(Permission permission) {
        return isGrantable(permission.getCode(), GrantTarget.CLIENT_ROLE);
    }

    private void requireFlag(String permissionCode, GrantTarget target) {
        if (!isGrantable(permissionCode, target)) {
            throw new InvalidPermissionGrantException(
                    "Permission " + permissionCode + " cannot be granted to " + target.label + ".");
        }
    }

    private boolean isGrantable(String permissionCode, GrantTarget target) {
        FeatureDefinition definition = findDefinition(permissionCode);
        return definition != null && switch (target) {
            case CLIENT_ROLE -> definition.clientRoleGrantable();
            case PLATFORM_ADMIN_ROLE -> definition.platformAdminRoleGrantable();
            case B2B_DELEGATION -> definition.isB2bDelegatablePermission(permissionCode);
        };
    }

    private FeatureDefinition findDefinition(String permissionCode) {
        String featureCode = featureCode(permissionCode);
        if (featureCode == null) {
            return null;
        }
        Map<String, FeatureDefinition> definitions = featureDefinitionCollectorProvider.getObject().collectByCode();
        FeatureDefinition definition = definitions.get(featureCode);
        if (definition == null || !definition.ownsPermission(permissionCode)) {
            return null;
        }
        return definition;
    }

    private static String featureCode(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return null;
        }
        int lastDot = permissionCode.lastIndexOf('.');
        if (lastDot < 1) {
            return null;
        }
        return permissionCode.substring(0, lastDot);
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
