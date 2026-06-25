package com.hiveapp.platform.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hiveapp.admin.bootstrap")
public record AdminBootstrapProperties(
        boolean enabled,
        String email,
        String password,
        String firstName,
        String lastName) {

    public AdminBootstrapProperties {
        firstName = defaultIfBlank(firstName, "Platform");
        lastName = defaultIfBlank(lastName, "Administrator");
    }

    public void validateForBootstrap() {
        if (!enabled) {
            throw new IllegalStateException("Admin bootstrap is disabled");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("Admin bootstrap email is required when bootstrap is enabled");
        }
        if (password == null || password.length() < 12) {
            throw new IllegalStateException("Admin bootstrap password must contain at least 12 characters");
        }
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
