package com.hiveapp.identity.event;

import com.hiveapp.identity.domain.constant.CredentialTokenPurpose;
import com.hiveapp.shared.config.ActivationProperties;
import com.hiveapp.shared.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class CredentialEmailListener {

    private final EmailService emailService;
    private final ActivationProperties activationProperties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(CredentialEmailRequestedEvent event) {
        String path = event.purpose() == CredentialTokenPurpose.ACTIVATION
                ? "/activate?token="
                : "/reset-password?token=";
        String url = activationProperties.getValidatedOrigin() + path + event.rawToken();
        try {
            emailService.sendCredentialLink(
                    event.email(),
                    event.memberName(),
                    event.workspaceName(),
                    url,
                    event.purpose(),
                    event.expiresAt());
        } catch (RuntimeException ex) {
            // The identity is already committed. Keep it pending so an account manager can
            // safely regenerate access instead of returning a false rollback to the client.
            log.error("Credential email delivery failed for user email={} purpose={}",
                    event.email(), event.purpose(), ex);
        }
    }
}
