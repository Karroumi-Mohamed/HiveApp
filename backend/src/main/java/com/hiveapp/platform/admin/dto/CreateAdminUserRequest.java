package com.hiveapp.platform.admin.dto;

import com.hiveapp.identity.domain.constant.InitialAccessMethod;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Creates a platform operator and the identity behind them in one step.
 *
 * <p>There is deliberately no {@code userId}: operators are never selected from the client user
 * pool. See {@link com.hiveapp.identity.domain.constant.IdentityKind}.
 */
public record CreateAdminUserRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 320) String email,
        @NotNull InitialAccessMethod initialAccessMethod,
        boolean isSuperAdmin
) {}
