package com.hiveapp.platform.client.member.dto;

import com.hiveapp.identity.domain.constant.CredentialState;
import com.hiveapp.identity.domain.constant.InitialAccessMethod;
import com.hiveapp.shared.email.delivery.EmailDeliverySummary;

import java.time.Instant;
import java.util.UUID;

public record MemberAccessStatusResponse(
        UUID memberId,
        InitialAccessMethod method,
        CredentialState credentialState,
        Instant linkExpiresAt,
        EmailDeliverySummary latestEmailDelivery
) {
}
