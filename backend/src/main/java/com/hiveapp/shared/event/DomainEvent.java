package com.hiveapp.shared.event;

import java.time.Instant;
import java.util.UUID;

public interface DomainEvent {
    UUID getEventId();
    Instant getOccurredAt();
}
