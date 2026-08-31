package com.hiveapp.identity.event;

import com.hiveapp.identity.domain.constant.CredentialTokenPurpose;
import com.hiveapp.shared.config.ActivationProperties;
import com.hiveapp.shared.email.EmailDispatchOutcome;
import com.hiveapp.shared.email.EmailService;
import com.hiveapp.shared.email.delivery.EmailDeliveryTracker;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CredentialEmailListenerTest {

    @Test
    void sentTransportIsNotMisreportedAsFailedWhenOutcomePersistenceFails() {
        EmailService emailService = mock(EmailService.class);
        EmailDeliveryTracker tracker = mock(EmailDeliveryTracker.class);
        ActivationProperties properties = mock(ActivationProperties.class);
        UUID deliveryId = UUID.randomUUID();
        when(properties.getValidatedOrigin()).thenReturn(URI.create("https://app.example"));
        when(emailService.sendCredentialLink(
                any(), any(), any(), any(), any(), any()))
                .thenReturn(EmailDispatchOutcome.SENT);
        doThrow(new IllegalStateException("database unavailable"))
                .when(tracker).recordOutcome(deliveryId, EmailDispatchOutcome.SENT);
        var listener = new CredentialEmailListener(emailService, tracker, properties);
        var event = new CredentialEmailRequestedEvent(
                deliveryId,
                "member@example.com",
                "Member",
                "Workspace",
                "raw-token",
                CredentialTokenPurpose.ACTIVATION,
                Instant.parse("2030-04-05T06:07:08Z"),
                CredentialEmailRequestedEvent.CredentialAudience.CLIENT);

        assertThatCode(() -> listener.send(event)).doesNotThrowAnyException();

        verify(tracker, never()).recordFailure(any(), any());
    }
}
