package com.hiveapp.shared.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Local/test fallback. It deliberately never logs credential-bearing links.
 */
/*
 * Active only when spring.mail.host is unset — the exact inverse of SmtpEmailServiceImpl, so
 * the two can never both apply.
 *
 * havingValue is a sentinel that is never configured: when the property is present the condition
 * fails, and when it is absent matchIfMissing carries it. This replaces an earlier
 * @ConditionalOnMissingBean(JavaMailSender.class) + @Primary pair, which could not work —
 * conditions on a user @Service are evaluated before auto-configuration registers JavaMailSender,
 * so the condition always passed and @Primary meant SMTP could never activate whatever was
 * configured. @ConditionalOnMissingBean is only reliable inside auto-configuration classes.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "spring.mail", name = "host",
        havingValue = "__never_configured__", matchIfMissing = true)
@Profile("!prod")
public class LoggingEmailServiceImpl implements EmailService {

    @Override
    public EmailDispatchOutcome sendCredentialLink(
            String to,
            String memberName,
            String workspaceName,
            String actionUrl,
            com.hiveapp.identity.domain.constant.CredentialTokenPurpose purpose,
            java.time.Instant expiresAt
    ) {
        log.info("[DEV] Credential email suppressed: to={}, purpose={}, workspace={}, expiresAt={}",
                to, purpose, workspaceName, expiresAt);
        return EmailDispatchOutcome.SUPPRESSED;
    }
}
