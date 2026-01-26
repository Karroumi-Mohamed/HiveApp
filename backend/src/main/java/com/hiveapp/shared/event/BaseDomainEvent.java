package com.hiveapp.shared.event;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Base implementation for domain events.
 * Extend this for concrete events in each module.
 */
@Getter
public abstract class BaseDomainEvent implements DomainEvent {

    private final UUID eventId;
    private final Instant occurredAt;
    private final String eventType;

    protected BaseDomainEvent() {
        this.eventId = UUID.randomUUID();
        this.occurredAt = Instant.now();
        this.eventType = this.getClass().getSimpleName();
    }
}
