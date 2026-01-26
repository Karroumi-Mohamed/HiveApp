package com.hiveapp.shared.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Base interface for all domain events in HiveApp.
 * Used by Spring Modulith's event system for inter-module communication.
 */
public interface DomainEvent {

    UUID getEventId();

    Instant getOccurredAt();

    String getEventType();
}
