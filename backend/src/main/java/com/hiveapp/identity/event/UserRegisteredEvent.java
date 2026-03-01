package com.hiveapp.identity.event;

import java.time.Instant;
import java.util.UUID;

import com.hiveapp.shared.event.DomainEvent;

public record UserRegisteredEvent(
    UUID userId,
    String email,
    UUID eventId,
    Instant occurredAt
) implements DomainEvent {
    public UserRegisteredEvent(UUID userId, String email) {
        this(userId, email, UUID.randomUUID(), Instant.now());
    }

    @Override
    public UUID getEventId() {
        return eventId;
    }

    @Override
    public Instant getOccurredAt() {
        return occurredAt;
    }
}