package com.hiveapp.shared.email;

import com.hiveapp.identity.domain.constant.CredentialTokenPurpose;

import java.time.Instant;

public interface EmailService {

    void sendCredentialLink(
            String to,
            String memberName,
            String workspaceName,
            String actionUrl,
            CredentialTokenPurpose purpose,
            Instant expiresAt);
}
