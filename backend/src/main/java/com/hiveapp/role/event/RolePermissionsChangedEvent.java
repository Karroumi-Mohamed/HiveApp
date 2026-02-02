package com.hiveapp.role.event;

import com.hiveapp.shared.event.BaseDomainEvent;
import lombok.Getter;

import java.util.UUID;

@Getter
public class RolePermissionsChangedEvent extends BaseDomainEvent {

    private final UUID roleId;
    private final UUID accountId;

    public RolePermissionsChangedEvent(UUID roleId, UUID accountId) {
        super();
        this.roleId = roleId;
        this.accountId = accountId;
    }
}
