package com.hiveapp.plan.event;

import com.hiveapp.shared.event.BaseDomainEvent;
import lombok.Getter;

import java.util.UUID;

@Getter
public class PlanChangedEvent extends BaseDomainEvent {

    private final UUID planId;
    private final UUID accountId;
    private final UUID oldPlanId;
    private final UUID newPlanId;

    public PlanChangedEvent(UUID planId, UUID accountId, UUID oldPlanId, UUID newPlanId) {
        super();
        this.planId = planId;
        this.accountId = accountId;
        this.oldPlanId = oldPlanId;
        this.newPlanId = newPlanId;
    }
}
