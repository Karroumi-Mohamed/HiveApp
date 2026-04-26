package com.hiveapp.platform.client.invitation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInvitationRequest(
        @NotBlank String token,

        /**
         * Required only when the invitee has no existing HiveApp account.
         * If the email already has an account, these fields are ignored.
         */
        String firstName,
        String lastName,
        @Size(min = 8, message = "Password must be at least 8 characters") String password
) {}
