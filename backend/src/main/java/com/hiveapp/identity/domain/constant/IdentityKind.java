package com.hiveapp.identity.domain.constant;

/**
 * Which side of the platform an identity belongs to.
 *
 * <p>Platform operators are created directly and never selected from, promoted out of, or
 * otherwise imported from the client user pool: the two carry different trust levels, so they
 * are different identities even when they are the same human. An {@code AdminUser} may only
 * reference a {@link #PLATFORM} identity.
 *
 * <p>One {@code users} table still backs both, deliberately. Email uniqueness is a database
 * guarantee on that single table; splitting the table would downgrade it to a cross-table
 * application check that can race two concurrent inserts. The credential state machine lives
 * here too and must not be duplicated.
 */
public enum IdentityKind {
    /** A member of a client account. Created through member management. */
    CLIENT,

    /** A HiveApp operator. Created through platform administration. */
    PLATFORM
}
