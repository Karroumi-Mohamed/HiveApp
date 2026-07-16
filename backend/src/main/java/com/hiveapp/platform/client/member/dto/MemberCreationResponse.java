package com.hiveapp.platform.client.member.dto;

import com.hiveapp.identity.domain.constant.CredentialState;
import com.hiveapp.identity.domain.constant.InitialAccessMethod;

import java.time.Instant;

public record MemberCreationResponse(
        MemberDto member,
        InitialAccessMethod initialAccessMethod,
        CredentialState credentialState,
        String temporaryPassword,
        Instant activationLinkExpiresAt
) {
}
