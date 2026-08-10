package com.hiveapp.platform.registry.dto;

public enum PermissionUnavailableReason {
    NOT_IN_CURRENT_REGISTRY,
    NOT_ENTITLED,
    NOT_GRANTABLE_FOR_AUDIENCE,
    NEW_GRANTS_PAUSED,
    EMERGENCY_RUNTIME_DISABLED
}
