package com.hiveapp.shared.email;

import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Real SMTP email service. Active only when spring.mail.host is configured
 * and Spring creates a JavaMailSender bean.
 */
@Slf4j
@Service
@ConditionalOnBean(JavaMailSender.class)
@RequiredArgsConstructor
public class SmtpEmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.from:noreply@hiveapp.io}")
    private String from;

    @Override
    public void sendInvitation(String to, String inviterName, String workspaceName, String acceptUrl) {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(inviterName + " invited you to join " + workspaceName + " on HiveApp");
            helper.setText(buildInvitationHtml(inviterName, workspaceName, acceptUrl), true);

            mailSender.send(message);
            log.info("Invitation email sent to={}", to);
        } catch (MessagingException e) {
            log.error("Failed to send invitation email to={}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Failed to send invitation email", e);
        }
    }

    // ── Template ──────────────────────────────────────────────────────────────

    private String buildInvitationHtml(String inviterName, String workspaceName, String acceptUrl) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>Workspace Invitation</title>
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
                              <h1 style="margin:0 0 16px;font-size:22px;font-weight:700;color:#1a1a2e;">You've been invited!</h1>
                              <p style="margin:0 0 24px;font-size:15px;color:#555;line-height:1.6;">
                                <strong>%s</strong> has invited you to join the workspace
                                <strong>%s</strong> on HiveApp.
                              </p>
                              <table cellpadding="0" cellspacing="0">
                                <tr>
                                  <td style="border-radius:6px;background:#4f46e5;">
                                    <a href="%s"
                                       style="display:inline-block;padding:14px 32px;color:#ffffff;font-size:15px;font-weight:600;text-decoration:none;letter-spacing:0.2px;">
                                      Accept Invitation →
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
                                This invitation expires in 7 days. If you didn't expect this email, you can safely ignore it.
                              </p>
                            </td>
                          </tr>

                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(inviterName, workspaceName, acceptUrl, acceptUrl, acceptUrl);
    }
}
