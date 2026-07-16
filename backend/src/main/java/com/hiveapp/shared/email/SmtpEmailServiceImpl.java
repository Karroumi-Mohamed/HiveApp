package com.hiveapp.shared.email;

import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import com.hiveapp.identity.domain.constant.CredentialTokenPurpose;

/**
 * Real SMTP email service. Active only when spring.mail.host is configured
 * and Spring creates a JavaMailSender bean.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "spring.mail", name = "host")
@RequiredArgsConstructor
public class SmtpEmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.from:noreply@hiveapp.io}")
    private String from;

    @Override
    public void sendCredentialLink(
            String to,
            String memberName,
            String workspaceName,
            String actionUrl,
            CredentialTokenPurpose purpose,
            Instant expiresAt
    ) {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(from);
            helper.setTo(to);
            String action = purpose == CredentialTokenPurpose.ACTIVATION
                    ? "Activate your HiveApp access"
                    : "Reset your HiveApp password";
            helper.setSubject(action + " for " + workspaceName);
            helper.setText(buildCredentialHtml(
                    memberName, workspaceName, actionUrl, purpose, expiresAt), true);

            mailSender.send(message);
            log.info("Credential email sent to={} purpose={}", to, purpose);
        } catch (MessagingException e) {
            log.error("Failed to send credential email to={}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Failed to send credential email", e);
        }
    }

    // ── Template ──────────────────────────────────────────────────────────────

    private String buildCredentialHtml(
            String memberName,
            String workspaceName,
            String actionUrl,
            CredentialTokenPurpose purpose,
            Instant expiresAt
    ) {
        String safeName = HtmlUtils.htmlEscape(memberName);
        String safeWorkspace = HtmlUtils.htmlEscape(workspaceName);
        String safeUrl = HtmlUtils.htmlEscape(actionUrl);
        String safeDeadline = HtmlUtils.htmlEscape(DateTimeFormatter.ISO_INSTANT.format(expiresAt));
        String heading = purpose == CredentialTokenPurpose.ACTIVATION
                ? "Activate your account"
                : "Reset your password";
        String button = purpose == CredentialTokenPurpose.ACTIVATION
                ? "Choose password and activate →"
                : "Choose a new password →";
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>%s</title>
                </head>
                <body style="margin:0;padding:0;background:#f4f4f7;font-family:'Helvetica Neue',Helvetica,Arial,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f7;padding:40px 0;">
                    <tr>
                      <td align="center">
                        <table width="560" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.08);">

                          <!-- Header -->
                          <tr>
                            <td style="background:#1a1a2e;padding:32px 40px;text-align:center;">
                              <span style="color:#ffffff;font-size:24px;font-weight:700;letter-spacing:-0.5px;">HiveApp</span>
                            </td>
                          </tr>

                          <!-- Body -->
                          <tr>
                            <td style="padding:40px 40px 24px;">
                              <h1 style="margin:0 0 16px;font-size:22px;font-weight:700;color:#1a1a2e;">%s</h1>
                              <p style="margin:0 0 24px;font-size:15px;color:#555;line-height:1.6;">
                                Hello <strong>%s</strong>. Use this one-time link to manage your access to
                                <strong>%s</strong> on HiveApp.
                              </p>
                              <table cellpadding="0" cellspacing="0">
                                <tr>
                                  <td style="border-radius:6px;background:#4f46e5;">
                                    <a href="%s"
                                       style="display:inline-block;padding:14px 32px;color:#ffffff;font-size:15px;font-weight:600;text-decoration:none;letter-spacing:0.2px;">
                                      %s
                                    </a>
                                  </td>
                                </tr>
                              </table>
                              <p style="margin:24px 0 0;font-size:13px;color:#999;">
                                Or copy this link into your browser:<br/>
                                <a href="%s" style="color:#4f46e5;word-break:break-all;">%s</a>
                              </p>
                            </td>
                          </tr>

                          <!-- Footer -->
                          <tr>
                            <td style="padding:24px 40px;border-top:1px solid #eee;">
                              <p style="margin:0;font-size:12px;color:#aaa;line-height:1.5;">
                                This one-time link expires at %s. If you didn't request it, you can safely ignore this email.
                              </p>
                            </td>
                          </tr>

                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                heading, heading, safeName, safeWorkspace, safeUrl, button,
                safeUrl, safeUrl, safeDeadline);
    }
}
