package com.hiveapp.account.event;

import com.hiveapp.shared.event.BaseDomainEvent;
import lombok.Getter;

import java.util.UUID;

@Getter
public class AccountCreatedEvent extends BaseDomainEvent {

    private final UUID accountId;
    private final UUID ownerId;
    private final UUID planId;

    public AccountCreatedEvent(UUID accountId, UUID ownerId, UUID planId) {
        super();
        this.accountId = accountId;
        this.ownerId = ownerId;
        this.planId = planId;
    }
}
