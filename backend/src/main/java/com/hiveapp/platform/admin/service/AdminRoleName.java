package com.hiveapp.platform.admin.service;

import java.util.Locale;
import java.util.regex.Pattern;

public record AdminRoleName(String display, String normalized) {
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public static AdminRoleName of(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Role name is required.");
        }
        String display = WHITESPACE.matcher(value.trim()).replaceAll(" ");
        if (display.isBlank()) {
            throw new IllegalArgumentException("Role name is required.");
        }
        return new AdminRoleName(display, display.toLowerCase(Locale.ROOT));
    }
}
