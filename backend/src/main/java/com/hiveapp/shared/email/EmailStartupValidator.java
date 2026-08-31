package com.hiveapp.shared.email;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Produces a deterministic configuration error before production credential-email
 * components are wired without an SMTP transport.
 */
@Component(EmailStartupValidator.BEAN_NAME)
@RequiredArgsConstructor
public class EmailStartupValidator {

    public static final String BEAN_NAME = "emailStartupValidator";

    private final Environment environment;

    @PostConstruct
    public void validate() {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }

        if (!StringUtils.hasText(environment.getProperty("spring.mail.host"))) {
            throw new IllegalStateException(
                    "Production email requires a non-blank spring.mail.host SMTP configuration");
        }
    }
}
