package com.hiveapp.identity.service;

import com.hiveapp.identity.domain.entity.User;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

/**
 * The only door other domains use to reach identity.
 *
 * <p>Cross-domain rule: no other domain may use identity's repositories. Facts are read through
 * {@link UserView}. The managed {@code User} entity is reachable only through the two explicitly
 * named methods below, and only to create an identity or to establish a JPA relationship inside a
 * transaction — never as a general accessor for identity fields.</p>
 */
public interface IdentityService {

    /** Identity facts. Prefer this whenever a relationship is not being established. */
    Optional<UserView> findUserView(UUID id);

    /** Bulk identity facts for authorized cross-domain read models. */
    List<UserView> findUserViews(java.util.Collection<UUID> ids);


    /** Uniqueness questions belong to identity, not to its callers. */
    boolean usernameExists(String username);

    boolean emailExists(String email);

    /**
     * Creates an identity. Entity door — the returned row is managed so the caller can attach it
     * to its own aggregate in the same transaction.
     */
    User createUser(NewUserCommand command);

    /**
     * Entity door — returns the managed row for establishing a JPA relationship inside a
     * transaction. Read fields through {@link #findUserView(UUID)} instead.
     */
    User requireManagedUser(UUID id);

    /**
     * Corrects a person's name. Identity owns the row, so the rename happens here rather than by
     * handing the entity out and letting another domain write to it.
     */
    UserView renameUser(UUID userId, String firstName, String lastName);

    /** Changes the login email and clears mailbox verification only when the canonical value changes. */
    boolean changeEmail(UUID userId, String email);
}
