package com.hiveapp.collaboration.event;

import com.hiveapp.shared.event.BaseDomainEvent;
import lombok.Getter;

import java.util.UUID;

@Getter
public class CollaborationPermissionsChangedEvent extends BaseDomainEvent {

    private final UUID collaborationId;
    private final UUID providerAccountId;

    public CollaborationPermissionsChangedEvent(UUID collaborationId, UUID providerAccountId) {
        super();
        this.collaborationId = collaborationId;
        this.providerAccountId = providerAccountId;
    }
}
