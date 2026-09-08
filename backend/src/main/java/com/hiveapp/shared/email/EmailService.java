package com.hiveapp.shared.email;

import com.hiveapp.identity.domain.constant.CredentialTokenPurpose;

import java.time.Instant;

public interface EmailService {
    default EmailDispatchOutcome sendCommercialNotice(String to, String subject, String text) {
        throw new UnsupportedOperationException("Commercial notice delivery is not configured");
    }

    EmailDispatchOutcome sendCredentialLink(
            String to,
            String memberName,
            String workspaceName,
            String actionUrl,
            CredentialTokenPurpose purpose,
            Instant expiresAt);
}
