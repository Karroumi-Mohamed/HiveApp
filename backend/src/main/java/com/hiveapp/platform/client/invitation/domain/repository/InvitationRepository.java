package com.hiveapp.platform.client.invitation.domain.repository;

import com.hiveapp.platform.client.invitation.domain.constant.InvitationStatus;
import com.hiveapp.platform.client.invitation.domain.entity.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    Optional<Invitation> findByToken(String token);

    /** All active invites for an account — used in list endpoint. */
    List<Invitation> findAllByAccountIdAndStatus(UUID accountId, InvitationStatus status);

    /** Check for an existing pending invite to the same email in the same workspace. */
    boolean existsByAccountIdAndEmailAndStatus(UUID accountId, String email, InvitationStatus status);

    /** Bulk-expire tokens past their expiry date (called by a scheduled job or on-demand). */
    List<Invitation> findAllByStatusAndExpiresAtBefore(InvitationStatus status, Instant now);
}
