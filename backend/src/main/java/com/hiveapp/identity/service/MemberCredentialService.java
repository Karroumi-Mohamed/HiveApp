package com.hiveapp.identity.service;

import com.hiveapp.identity.domain.constant.CredentialState;
import com.hiveapp.identity.domain.constant.CredentialTokenPurpose;
import com.hiveapp.identity.domain.constant.InitialAccessMethod;
import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.identity.event.CredentialEmailRequestedEvent;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.shared.config.ActivationProperties;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.security.TokenAudience;
import com.hiveapp.shared.security.TokenSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberCredentialService {

    private final CredentialSecretGenerator secretGenerator;
    private final PasswordEncoder passwordEncoder;
    private final ActivationProperties activationProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final TokenSessionService tokenSessionService;

    public CredentialAccessMaterial initialize(User user, Account account) {
        return hasEmail(user) ? emailAccess(user, account, CredentialTokenPurpose.ACTIVATION, true)
                : temporaryAccess(user);
    }

    public CredentialAccessMaterial regenerate(User user, Account account) {
        if (user.getCredentialState() == CredentialState.ACTIVE) {
            throw new InvalidStateException("Activated access must be reset, not regenerated");
        }
        tokenSessionService.revokeAll(List.of(user.getId()), TokenAudience.CLIENT);
        CredentialTokenPurpose purpose = user.isEmailVerified()
                ? CredentialTokenPurpose.PASSWORD_RESET
                : CredentialTokenPurpose.ACTIVATION;
        return hasEmail(user) ? emailAccess(user, account, purpose, true)
                : temporaryAccess(user);
    }

    public CredentialAccessMaterial reset(User user, Account account) {
        if (user.getCredentialState() != CredentialState.ACTIVE) {
            throw new InvalidStateException("Unactivated access must be regenerated, not reset");
        }
        tokenSessionService.revokeAll(List.of(user.getId()), TokenAudience.CLIENT);
        return hasEmail(user) ? emailAccess(user, account, CredentialTokenPurpose.PASSWORD_RESET, true)
                : temporaryAccess(user);
    }

    public CredentialAccessMaterial requestSelfServiceReset(User user, Account account) {
        if (!hasEmail(user) || !user.isEmailVerified()) {
            throw new InvalidStateException("Verified email is required for self-service password recovery");
        }
        return emailAccess(user, account, CredentialTokenPurpose.PASSWORD_RESET, false);
    }

    public void invalidatePendingAccess(User user) {
        clearToken(user);
        if (user.getCredentialState() != CredentialState.ACTIVE) {
            user.setPasswordHash(null);
            user.setPasswordChangeRequired(true);
            user.setInitialAccessLocked(false);
            user.setInitialAccessFailedAttempts(0);
        }
        tokenSessionService.revokeAll(List.of(user.getId()), TokenAudience.CLIENT);
    }

    public void unlock(User user) {
        if (!user.isInitialAccessLocked()) {
            throw new InvalidStateException("Initial access is not locked");
        }
        user.setInitialAccessLocked(false);
        user.setInitialAccessFailedAttempts(0);
    }

    private CredentialAccessMaterial temporaryAccess(User user) {
        String temporaryPassword = secretGenerator.temporaryPassword();
        clearToken(user);
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setCredentialState(CredentialState.TEMPORARY_PASSWORD);
        user.setPasswordChangeRequired(true);
        user.setInitialAccessLocked(false);
        user.setInitialAccessFailedAttempts(0);
        return new CredentialAccessMaterial(
                InitialAccessMethod.TEMPORARY_PASSWORD,
                user.getCredentialState(),
                temporaryPassword,
                null);
    }

    private CredentialAccessMaterial emailAccess(
            User user,
            Account account,
            CredentialTokenPurpose purpose,
            boolean blockExistingAccess
    ) {
        String rawToken = secretGenerator.activationToken();
        Instant expiresAt = Instant.now().plus(activationProperties.getTokenExpiration());
        user.setCredentialTokenHash(secretGenerator.hashToken(rawToken));
        user.setCredentialTokenPurpose(purpose);
        user.setCredentialTokenExpiresAt(expiresAt);
        user.setInitialAccessLocked(false);
        user.setInitialAccessFailedAttempts(0);
        if (blockExistingAccess) {
            user.setCredentialState(purpose == CredentialTokenPurpose.ACTIVATION
                    ? CredentialState.EMAIL_ACTIVATION_PENDING
                    : CredentialState.EMAIL_RESET_PENDING);
            user.setPasswordChangeRequired(true);
        }
        eventPublisher.publishEvent(new CredentialEmailRequestedEvent(
                user.getEmail(), user.getFullName(), account.getName(), rawToken, purpose, expiresAt));
        return new CredentialAccessMaterial(
                InitialAccessMethod.EMAIL_LINK,
                user.getCredentialState(),
                null,
                expiresAt);
    }

    public void clearToken(User user) {
        user.setCredentialTokenHash(null);
        user.setCredentialTokenPurpose(null);
        user.setCredentialTokenExpiresAt(null);
    }

    private boolean hasEmail(User user) {
        return user.getEmail() != null && !user.getEmail().isBlank();
    }
}
