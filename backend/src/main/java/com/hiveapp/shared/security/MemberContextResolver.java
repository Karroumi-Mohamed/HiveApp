package com.hiveapp.shared.security;

import java.util.Optional;
import java.util.UUID;

/**
 * Strategy interface for resolving a user's member context during authentication.
 *
 * Implemented in the member module so the shared/security layer can look up
 * which member and account to attach to the security principal without
 * directly depending on the member module.
 */
public interface MemberContextResolver {

    /**
     * Find the member context for a specific user and account.
     *
     * @param userId    the authenticated user's ID
     * @param accountId the target account ID (from X-Account-Id header)
     * @return member context if the user has a membership in that account
     */
    Optional<MemberContext> resolve(UUID userId, UUID accountId);

    /**
     * Find the default member context for a user.
     * Returns the user's first membership (typically their own account).
     *
     * @param userId the authenticated user's ID
     * @return default member context, or empty if user has no memberships
     */
    Optional<MemberContext> resolveDefault(UUID userId);
}
