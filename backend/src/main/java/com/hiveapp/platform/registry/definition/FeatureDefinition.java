package com.hiveapp.platform.registry.definition;

import com.hiveapp.shared.quota.QuotaSlot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public record FeatureDefinition(
        String code,
        String moduleCode,
        String featureKey,
        String displayName,
        String description,
        FeatureSurface surface,
        boolean planAssignable,
        boolean clientRoleGrantable,
        boolean platformAdminRoleGrantable,
        boolean b2bDelegatable,
        boolean publicCatalogVisible,
        int sortOrder,
        List<QuotaSlot> quotaSlots,
        Set<String> b2bDelegatableActions
) {
    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*$");
    private static final Pattern ACTION_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*$");

    public FeatureDefinition {
        requireText(code, "Feature code is required");
        requireText(displayName, "Feature display name is required");
        Objects.requireNonNull(surface, "Feature surface is required");

        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new FeatureDefinitionException(
                    "Feature code must use exactly '<module>.<feature>' with lowercase snake-case segments: " + code);
        }

        String[] parts = code.split("\\.");
        moduleCode = parts[0];
        featureKey = parts[1];
        description = description == null ? "" : description;
        quotaSlots = List.copyOf(quotaSlots == null ? List.of() : quotaSlots);
        b2bDelegatableActions = Set.copyOf(b2bDelegatableActions == null ? Set.of() : b2bDelegatableActions);

        validateSurfaceFlags(code, surface, planAssignable, clientRoleGrantable,
                platformAdminRoleGrantable, b2bDelegatable, publicCatalogVisible);
        validateQuotaSlots(code, quotaSlots);
        validateB2bActions(code, surface, b2bDelegatable, b2bDelegatableActions);
    }

    public static Builder platformControl(String code) {
        return new Builder(code, FeatureSurface.PLATFORM_CONTROL)
                .platformAdminRoleGrantable(true);
    }

    public static Builder clientWorkspace(String code) {
        return new Builder(code, FeatureSurface.CLIENT_WORKSPACE)
                .planAssignable(true)
                .clientRoleGrantable(true)
                .publicCatalogVisible(true);
    }

    public static Builder publicFeature(String code) {
        return new Builder(code, FeatureSurface.PUBLIC)
                .publicCatalogVisible(true);
    }

    public static Builder system(String code) {
        return new Builder(code, FeatureSurface.SYSTEM);
    }

    public boolean ownsPermission(String permissionCode) {
        return permissionCode != null && permissionCode.startsWith(code + ".")
                && permissionCode.indexOf('.', code.length() + 1) == -1;
    }

    public boolean isB2bDelegatablePermission(String permissionCode) {
        if (!ownsPermission(permissionCode)) {
            return false;
        }
        return b2bDelegatableActions.contains(permissionCode.substring(code.length() + 1));
    }

    public static final class Builder {
        private final String code;
        private final FeatureSurface surface;
        private String displayName;
        private String description = "";
        private boolean planAssignable;
        private boolean clientRoleGrantable;
        private boolean platformAdminRoleGrantable;
        private boolean b2bDelegatable;
        private boolean publicCatalogVisible;
        private int sortOrder = 1000;
        private final List<QuotaSlot> quotaSlots = new ArrayList<>();
        private final Set<String> b2bDelegatableActions = new HashSet<>();

        private Builder(String code, FeatureSurface surface) {
            this.code = code;
            this.surface = surface;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder sortOrder(int sortOrder) {
            this.sortOrder = sortOrder;
            return this;
        }

        public Builder quota(QuotaSlot quotaSlot) {
            this.quotaSlots.add(Objects.requireNonNull(quotaSlot, "Quota slot is required"));
            return this;
        }

        public Builder quotas(List<QuotaSlot> quotaSlots) {
            this.quotaSlots.addAll(Objects.requireNonNull(quotaSlots, "Quota slots are required"));
            return this;
        }

        public Builder b2bDelegatable(boolean b2bDelegatable) {
            this.b2bDelegatable = b2bDelegatable;
            return this;
        }

        public Builder b2bDelegatableActions(String... actions) {
            Objects.requireNonNull(actions, "B2B delegatable actions are required");
            this.b2bDelegatable = true;
            this.b2bDelegatableActions.addAll(List.of(actions));
            return this;
        }

        private Builder planAssignable(boolean planAssignable) {
            this.planAssignable = planAssignable;
            return this;
        }

        private Builder clientRoleGrantable(boolean clientRoleGrantable) {
            this.clientRoleGrantable = clientRoleGrantable;
            return this;
        }

        private Builder platformAdminRoleGrantable(boolean platformAdminRoleGrantable) {
            this.platformAdminRoleGrantable = platformAdminRoleGrantable;
            return this;
        }

        private Builder publicCatalogVisible(boolean publicCatalogVisible) {
            this.publicCatalogVisible = publicCatalogVisible;
            return this;
        }

        public FeatureDefinition build() {
            return new FeatureDefinition(
                    code,
                    null,
                    null,
                    displayName,
                    description,
                    surface,
                    planAssignable,
                    clientRoleGrantable,
                    platformAdminRoleGrantable,
                    b2bDelegatable,
                    publicCatalogVisible,
                    sortOrder,
                    quotaSlots,
                    b2bDelegatableActions);
        }
    }

    private static void validateSurfaceFlags(
            String code,
            FeatureSurface surface,
            boolean planAssignable,
            boolean clientRoleGrantable,
            boolean platformAdminRoleGrantable,
            boolean b2bDelegatable,
            boolean publicCatalogVisible
    ) {
        if (planAssignable && !surface.planAssignable()) {
            throw new FeatureDefinitionException(code + " cannot be plan-assignable from surface " + surface);
        }
        if (clientRoleGrantable && !surface.clientRoleGrantable()) {
            throw new FeatureDefinitionException(code + " cannot be client-role grantable from surface " + surface);
        }
        if (platformAdminRoleGrantable && !surface.platformAdminRoleGrantable()) {
            throw new FeatureDefinitionException(code + " cannot be platform-admin-role grantable from surface " + surface);
        }
        if (b2bDelegatable && surface != FeatureSurface.CLIENT_WORKSPACE) {
            throw new FeatureDefinitionException(code + " cannot be B2B-delegatable from surface " + surface);
        }
        if (publicCatalogVisible && !surface.publicCatalogVisible()) {
            throw new FeatureDefinitionException(code + " cannot be public-catalog visible from surface " + surface);
        }
    }

    private static void validateQuotaSlots(String code, List<QuotaSlot> quotaSlots) {
        var resources = new java.util.HashSet<String>();
        for (QuotaSlot quotaSlot : quotaSlots) {
            requireText(quotaSlot.resource(), "Quota resource is required for " + code);
            Objects.requireNonNull(quotaSlot.type(), "Quota type is required for " + code + "." + quotaSlot.resource());
            requireText(quotaSlot.unit(), "Quota unit is required for " + code + "." + quotaSlot.resource());
            if (!resources.add(quotaSlot.resource())) {
                throw new FeatureDefinitionException(
                        "Duplicate quota resource '" + quotaSlot.resource() + "' for feature " + code);
            }
        }
    }

    private static void validateB2bActions(
            String code,
            FeatureSurface surface,
            boolean b2bDelegatable,
            Set<String> b2bDelegatableActions
    ) {
        if (!b2bDelegatableActions.isEmpty() && surface != FeatureSurface.CLIENT_WORKSPACE) {
            throw new FeatureDefinitionException(code + " cannot be B2B-delegatable from surface " + surface);
        }
        if (!b2bDelegatable && !b2bDelegatableActions.isEmpty()) {
            throw new FeatureDefinitionException(code + " cannot declare B2B actions when B2B delegation is disabled");
        }
        for (String action : b2bDelegatableActions) {
            requireText(action, "B2B delegatable action is required for " + code);
            if (!ACTION_PATTERN.matcher(action).matches()) {
                throw new FeatureDefinitionException(
                        "B2B delegatable action must be a lowercase snake-case action for " + code + ": " + action);
            }
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new FeatureDefinitionException(message);
        }
    }
}
