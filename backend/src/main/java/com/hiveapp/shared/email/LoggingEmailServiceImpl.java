package com.hiveapp.shared.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * No-op email service used when no SMTP server is configured (spring.mail.host absent).
 * Logs the invite link so it can be used directly during development / testing.
 */
@Slf4j
@Service
@Primary
@ConditionalOnMissingBean(JavaMailSender.class)
public class LoggingEmailServiceImpl implements EmailService {

    @Override
    public void sendInvitation(String to, String inviterName, String workspaceName, String acceptUrl) {
        log.info("""
                ╔══════════════════════════════════════════════════════╗
                ║  [DEV] Invitation Email — no SMTP configured        ║
                ╠══════════════════════════════════════════════════════╣
                ║  To:        {}
                ║  Inviter:   {}
                ║  Workspace: {}
                ║  Accept:    {}
                ╚══════════════════════════════════════════════════════╝
                """, to, inviterName, workspaceName, acceptUrl);
    }
}
