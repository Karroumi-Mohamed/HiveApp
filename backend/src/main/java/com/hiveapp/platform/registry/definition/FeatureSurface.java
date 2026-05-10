package com.hiveapp.platform.registry.definition;

/**
 * Code-owned security boundary for a feature.
 *
 * Lifecycle/status can change in the database, but the surface is part of the
 * source code contract and must not be admin-editable.
 */
public enum FeatureSurface {
    PLATFORM_CONTROL,
    CLIENT_WORKSPACE,
    PUBLIC,
    SYSTEM;

    public boolean planAssignable() {
        return this == CLIENT_WORKSPACE;
    }

    public boolean clientRoleGrantable() {
        return this == CLIENT_WORKSPACE;
    }

    public boolean platformAdminRoleGrantable() {
        return this == PLATFORM_CONTROL;
    }

    public boolean publicCatalogVisible() {
        return this == PUBLIC || this == CLIENT_WORKSPACE;
    }
}
