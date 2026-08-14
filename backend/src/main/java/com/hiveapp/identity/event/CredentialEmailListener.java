package com.hiveapp.identity.event;

import com.hiveapp.identity.domain.constant.CredentialTokenPurpose;
import com.hiveapp.shared.config.ActivationProperties;
import com.hiveapp.shared.email.EmailService;
import com.hiveapp.shared.email.EmailDeliveryException;
import com.hiveapp.shared.email.EmailStartupValidator;
import com.hiveapp.shared.email.delivery.EmailDeliveryFailureCode;
import com.hiveapp.shared.email.delivery.EmailDeliveryTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.DependsOn;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Objects;

@Component
@DependsOn(EmailStartupValidator.BEAN_NAME)
@RequiredArgsConstructor
@Slf4j
public class CredentialEmailListener {

    private final EmailService emailService;
    private final EmailDeliveryTracker deliveryTracker;
    private final ActivationProperties activationProperties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(CredentialEmailRequestedEvent event) {
        // These must match the router in App.tsx. An operator activates on the admin page, which
        // posts to the admin endpoint and issues no session.
        String path = switch (event.audience()) {
            case PLATFORM_OPERATOR -> switch (event.purpose()) {
                case ACTIVATION -> "/admin/activation/complete?token=";
                case PASSWORD_RESET -> "/admin/password-reset/complete?token=";
                case EMAIL_VERIFICATION -> "/admin/email-verification/complete?token=";
            };
            case CLIENT -> switch (event.purpose()) {
                case ACTIVATION -> "/app/activation/complete?token=";
                case PASSWORD_RESET -> "/app/password-reset/complete?token=";
                case EMAIL_VERIFICATION -> throw new IllegalStateException(
                        "Client email verification links are not implemented");
            };
        };
        String url = activationProperties.getValidatedOrigin() + path + event.rawToken();
        com.hiveapp.shared.email.EmailDispatchOutcome outcome;
        try {
            outcome = Objects.requireNonNull(emailService.sendCredentialLink(
                    event.email(),
                    event.memberName(),
                    event.workspaceName(),
                    url,
                    event.purpose(),
                    event.expiresAt()), "Email transport outcome is required");
        } catch (EmailDeliveryException ex) {
            recordFailure(event, ex.failureCode());
            return;
        } catch (RuntimeException ex) {
            recordFailure(event, EmailDeliveryFailureCode.UNEXPECTED_FAILURE);
            return;
        }
        try {
            deliveryTracker.recordOutcome(event.deliveryId(), outcome);
        } catch (RuntimeException persistenceFailure) {
            // Transport completed, but its durable outcome is unknown. Leaving PENDING is
            // safer than falsely recording FAILED and encouraging an immediate duplicate send.
            log.error("Credential email status persistence failed deliveryId={} purpose={}",
                    event.deliveryId(), event.purpose());
        }
    }

    private void recordFailure(
            CredentialEmailRequestedEvent event,
            EmailDeliveryFailureCode failureCode
    ) {
        try {
            deliveryTracker.recordFailure(event.deliveryId(), failureCode);
        } catch (RuntimeException persistenceFailure) {
            // The identity is already committed. Keep it pending so an account manager can
            // safely regenerate access instead of returning a false rollback to the client.
            log.error("Credential email status persistence failed deliveryId={} purpose={}",
                    event.deliveryId(), event.purpose());
            return;
        }
        log.error("Credential email delivery failed deliveryId={} purpose={} failureCode={}",
                event.deliveryId(), event.purpose(), failureCode);
    }
}
