package com.hiveapp.platform.client.invitation.service;

import com.hiveapp.platform.client.invitation.dto.InvitationDto;
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
}
