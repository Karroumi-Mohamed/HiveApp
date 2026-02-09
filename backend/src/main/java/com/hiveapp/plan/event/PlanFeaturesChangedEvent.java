package com.hiveapp.plan.event;

import com.hiveapp.shared.event.BaseDomainEvent;
import lombok.Getter;

import java.util.UUID;

/**
 * Published when features are added to or removed from a Plan.
 * Triggers cache eviction for plan ceilings across all accounts on this plan.
 */
@Getter
public class PlanFeaturesChangedEvent extends BaseDomainEvent {

    private final UUID planId;

    public PlanFeaturesChangedEvent(UUID planId) {
        super();
        this.planId = planId;
    }
}
