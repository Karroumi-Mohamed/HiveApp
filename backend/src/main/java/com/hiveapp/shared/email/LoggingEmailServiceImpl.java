package com.hiveapp.shared.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Local/test fallback. It deliberately never logs credential-bearing links.
 */
@Slf4j
@Service
@Primary
@ConditionalOnMissingBean(JavaMailSender.class)
@Profile("!prod")
public class LoggingEmailServiceImpl implements EmailService {

    @Override
    public void sendCredentialLink(
            String to,
            String memberName,
            String workspaceName,
            String actionUrl,
            com.hiveapp.identity.domain.constant.CredentialTokenPurpose purpose,
            java.time.Instant expiresAt
    ) {
        log.info("[DEV] Credential email suppressed: to={}, purpose={}, workspace={}, expiresAt={}",
                to, purpose, workspaceName, expiresAt);
    }
}
