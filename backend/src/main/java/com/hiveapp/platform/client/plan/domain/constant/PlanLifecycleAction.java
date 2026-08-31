package com.hiveapp.platform.client.plan.domain.constant;

public enum PlanLifecycleAction {
    ACTIVATE,
    DEACTIVATE,
    ARCHIVE;

    public PlanStatus targetStatus() {
        return switch (this) {
            case ACTIVATE -> PlanStatus.ACTIVE;
            case DEACTIVATE -> PlanStatus.INACTIVE;
            case ARCHIVE -> PlanStatus.ARCHIVED;
        };
    }
}
