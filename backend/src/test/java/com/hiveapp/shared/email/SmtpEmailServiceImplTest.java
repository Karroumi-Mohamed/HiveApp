package com.hiveapp.shared.email;

import com.hiveapp.identity.domain.constant.CredentialTokenPurpose;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SmtpEmailServiceImplTest {

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
