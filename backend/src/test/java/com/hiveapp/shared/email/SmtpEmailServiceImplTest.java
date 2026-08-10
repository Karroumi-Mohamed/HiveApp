package com.hiveapp.shared.email;

import com.hiveapp.identity.domain.constant.CredentialTokenPurpose;
import com.hiveapp.shared.email.delivery.EmailDeliveryFailureCode;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class SmtpEmailServiceImplTest {

    @Test
    void successfulTransportReportsSent() {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        var service = new SmtpEmailServiceImpl(sender);
        ReflectionTestUtils.setField(service, "from", "noreply@example.com");

        var outcome = service.sendCredentialLink(
                "member@example.com", "Member", "Workspace",
                "https://app.example/activate?token=secret",
                CredentialTokenPurpose.ACTIVATION,
                Instant.parse("2030-04-05T06:07:08Z"));

        assertThat(outcome).isEqualTo(EmailDispatchOutcome.SENT);
        verify(sender).send(message);
    }

    @Test
    void smtpFailurePersistsOnlyTheSafeCodeButLogsTheOperationalCause(CapturedOutput output) {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        doThrow(new MailSendException("provider detail must not become API state"))
                .when(sender).send(any(MimeMessage.class));
        var service = new SmtpEmailServiceImpl(sender);
        ReflectionTestUtils.setField(service, "from", "noreply@example.com");

        assertThatThrownBy(() -> service.sendCredentialLink(
                "member@example.com", "Member", "Workspace",
                "https://app.example/activate?token=secret",
                CredentialTokenPurpose.ACTIVATION,
                Instant.parse("2030-04-05T06:07:08Z")))
                .isInstanceOfSatisfying(EmailDeliveryException.class, failure ->
                        assertThat(failure.failureCode())
                                .isEqualTo(EmailDeliveryFailureCode.TRANSPORT_FAILED))
                .hasMessage("Email delivery failed");

        assertThat(output)
                .contains("Credential email transport failed")
                .contains("provider detail must not become API state")
                .doesNotContain("activate?token=secret");
    }

    @Test
    void credentialTemplateEscapesDynamicValuesAndUsesTheActualDeadline() {
        var service = new SmtpEmailServiceImpl(mock(JavaMailSender.class));
        Instant expiresAt = Instant.parse("2030-04-05T06:07:08Z");

        String html = ReflectionTestUtils.invokeMethod(
                service,
                "buildCredentialHtml",
                "<script>alert('name')</script>",
                "A&B <Workspace>",
                "https://app.example/activate?token=\"secret\"&next=<home>",
                CredentialTokenPurpose.ACTIVATION,
                expiresAt);

        assertThat(html)
                .contains("&lt;script&gt;alert(&#39;name&#39;)&lt;/script&gt;")
                .contains("A&amp;B &lt;Workspace&gt;")
                .contains("token=&quot;secret&quot;&amp;next=&lt;home&gt;")
                .contains("2030-04-05T06:07:08Z")
                .doesNotContain("<script>")
                .doesNotContain("7 days");
    }
}
