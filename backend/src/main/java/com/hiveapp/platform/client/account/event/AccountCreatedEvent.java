package com.hiveapp.platform.client.account.event;

import java.time.Instant;
import java.util.UUID;

import com.hiveapp.shared.event.DomainEvent;

public record AccountCreatedEvent(
    UUID accountId,
    UUID userId,
    UUID eventId,
    Instant occurredAt
) implements DomainEvent {
    public AccountCreatedEvent(UUID accountId, UUID userId) {
        this(accountId, userId, UUID.randomUUID(), Instant.now());
    }

    @Override public UUID getEventId() { return eventId; }
    @Override public Instant getOccurredAt() { return occurredAt; }
}