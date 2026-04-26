package com.hiveapp.platform.client.invitation.service;

import com.hiveapp.identity.dto.AuthResponse;
import com.hiveapp.platform.client.invitation.dto.AcceptInvitationRequest;
import com.hiveapp.platform.client.invitation.dto.InvitationDto;
import com.hiveapp.platform.client.invitation.dto.InvitationInfoDto;
import com.hiveapp.platform.client.invitation.dto.SendInvitationRequest;

import java.util.List;
import java.util.UUID;

public interface InvitationService {

    /** Send invite email and persist the invitation record. */
    InvitationDto send(UUID accountId, UUID invitedByUserId, SendInvitationRequest request);

    /** List all PENDING invitations for an account. */
    List<InvitationDto> listPending(UUID accountId);

    /** Revoke a PENDING invitation. */
    void revoke(UUID invitationId, UUID accountId);

    /**
     * Validate a token without accepting it.
     * Used by the UI to pre-fill the accept page (show workspace name, inviter, etc.).
     */
    InvitationInfoDto validate(String token);

    /**
     * Accept an invitation.
     * If the email has no account yet, firstName + lastName + password are required.
     * If the email already has an account, those fields are ignored.
     * Returns a CLIENT JWT scoped to the invited workspace.
     */
    AuthResponse accept(AcceptInvitationRequest request);
}
