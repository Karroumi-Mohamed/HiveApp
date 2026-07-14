package com.hiveapp.identity.domain;

import java.util.Locale;

public final class EmailIdentity {

    private EmailIdentity() {
    }

    public static String canonicalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
