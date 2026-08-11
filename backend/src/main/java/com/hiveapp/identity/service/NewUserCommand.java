package com.hiveapp.identity.service;

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
        boolean emailVerified
) {
    public static NewUserCommand withoutCredentials(
            String username, String email, String firstName, String lastName, String phone) {
        return new NewUserCommand(username, email, firstName, lastName, phone, null, true, false);
    }
}
