package com.hiveapp.platform.client.plan.domain.constant;

public enum AddOnLifecycleAction {
    ACTIVATE,
    DEACTIVATE,
    ARCHIVE;

    public AddOnStatus targetStatus() {
        return switch (this) {
            case ACTIVATE -> AddOnStatus.ACTIVE;
            case DEACTIVATE -> AddOnStatus.INACTIVE;
            case ARCHIVE -> AddOnStatus.ARCHIVED;
        };
    }
}
