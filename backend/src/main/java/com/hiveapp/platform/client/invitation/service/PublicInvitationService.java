package com.hiveapp.platform.client.invitation.service;

import com.hiveapp.identity.dto.AuthResponse;
import com.hiveapp.platform.client.invitation.dto.AcceptInvitationRequest;
import com.hiveapp.platform.client.invitation.dto.InvitationInfoDto;

public interface PublicInvitationService {

    /**
     * Validate a token without accepting it.
     * Used by the UI to pre-fill the accept page.
     */
    InvitationInfoDto validate(String token);

    /**
     * Redeem a pending invitation token and return a client session.
     */
    AuthResponse accept(AcceptInvitationRequest request);
}
