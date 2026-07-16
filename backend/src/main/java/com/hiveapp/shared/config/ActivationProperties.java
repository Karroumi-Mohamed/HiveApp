package com.hiveapp.shared.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "hiveapp.activation")
@RequiredArgsConstructor
public class ActivationProperties {

    private final Environment environment;
    private String baseUrl;
    private Duration tokenExpiration = Duration.ofHours(24);
    private URI validatedOrigin;

    @PostConstruct
    void validate() {
        try {
            URI origin = URI.create(baseUrl);
            boolean validScheme = "https".equalsIgnoreCase(origin.getScheme())
                    || "http".equalsIgnoreCase(origin.getScheme());
            if (!origin.isAbsolute() || !validScheme || origin.getHost() == null
                    || origin.getUserInfo() != null || origin.getQuery() != null
                    || origin.getFragment() != null) {
                throw new IllegalArgumentException();
            }
            boolean production = Arrays.asList(environment.getActiveProfiles()).contains("prod");
            if (production && !"https".equalsIgnoreCase(origin.getScheme())) {
                throw new IllegalStateException("Production activation origin must use HTTPS");
            }
            String normalized = origin.toString();
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            validatedOrigin = URI.create(normalized);
            if (tokenExpiration == null || tokenExpiration.isZero() || tokenExpiration.isNegative()) {
                throw new IllegalStateException("Activation token expiration must be positive");
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("hiveapp.activation.base-url must be a valid HTTP(S) origin", ex);
        }
    }
}
