package com.hiveapp.platform.client.invitation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record SendInvitationRequest(
        @NotBlank @Email String email,

        /** Optional: pre-assign this role when the invite is accepted. */
        UUID roleId,

        /** Optional: scope the role to a specific company. */
        UUID companyId
) {}
