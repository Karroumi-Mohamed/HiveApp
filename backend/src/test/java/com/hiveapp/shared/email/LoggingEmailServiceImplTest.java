package com.hiveapp.shared.email;

import com.hiveapp.identity.domain.constant.CredentialTokenPurpose;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingEmailServiceImplTest {

    @Test
    void developmentFallbackReportsSuppressedInsteadOfDelivered() {
        var service = new LoggingEmailServiceImpl();

        var outcome = service.sendCredentialLink(
                "member@example.com", "Member", "Workspace",
                "https://app.example/activate?token=must-not-be-logged",
                CredentialTokenPurpose.ACTIVATION,
                Instant.parse("2030-04-05T06:07:08Z"));

        assertThat(outcome).isEqualTo(EmailDispatchOutcome.SUPPRESSED);
    }
}
