package com.hiveapp.identity.service;

import java.util.UUID;

/**
 * Immutable identity facts published to other domains.
 *
 * <p>Consumers that only need to read identity data take this rather than the {@code User}
 * entity, so they cannot couple to identity's persistence model or mutate a managed row.</p>
 */
public record UserView(
        UUID id,
        String username,
        String email,
        String firstName,
        String lastName,
        boolean active
) {
}
