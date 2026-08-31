package com.hiveapp.identity.service;

import com.hiveapp.identity.domain.constant.IdentityKind;

/**
 * Request to create an identity. Identity owns the rules that apply to it — email
 * canonicalization, uniqueness, and credential material — so other domains describe the person
 * they need and let identity construct the row.
 */
public record NewUserCommand(
        String username,
        String email,
        String firstName,
        String lastName,
        String phone,
        String passwordHash,
        boolean active,
        boolean emailVerified,
        IdentityKind kind
) {
    public static NewUserCommand withoutCredentials(
            String username, String email, String firstName, String lastName, String phone) {
        return new NewUserCommand(
                username, email, firstName, lastName, phone, null, true, false, IdentityKind.CLIENT);
    }

    /**
     * A platform operator. Credentials are issued afterwards through the operator activation
     * path rather than supplied here, so the row starts without a password.
     */
    public static NewUserCommand platformOperator(
            String username, String email, String firstName, String lastName) {
        return new NewUserCommand(
                username, email, firstName, lastName, null, null, true, false, IdentityKind.PLATFORM);
    }
}
