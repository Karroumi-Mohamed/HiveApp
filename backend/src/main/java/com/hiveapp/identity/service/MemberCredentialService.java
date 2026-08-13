package com.hiveapp.identity.service;

import com.hiveapp.identity.domain.constant.CredentialState;
import com.hiveapp.identity.domain.constant.CredentialTokenPurpose;
import com.hiveapp.identity.domain.constant.IdentityKind;
import com.hiveapp.identity.domain.constant.InitialAccessMethod;
import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.identity.event.CredentialEmailRequestedEvent;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.shared.config.ActivationProperties;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.security.TokenAudience;
import com.hiveapp.shared.security.TokenSessionService;
import com.hiveapp.shared.email.delivery.EmailDeliveryTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemberCredentialService {

    private final CredentialSecretGenerator secretGenerator;
    private final PasswordEncoder passwordEncoder;
    private final ActivationProperties activationProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final TokenSessionService tokenSessionService;
    private final EmailDeliveryTracker emailDeliveryTracker;
    private final UserRepository userRepository;

    /** Shown to an operator where a client account name would appear for a member. */
    private static final String PLATFORM_ORGANISATION_NAME = "HiveApp";

    public CredentialAccessMaterial initialize(User user, Account account) {
        return persisted(user, hasEmail(user)
                ? emailAccess(user, requirePersistedAccount(account), account.getName(),
                        CredentialTokenPurpose.ACTIVATION, true)
                : temporaryAccess(user));
    }

    /**
     * Initial access for a platform operator, who belongs to no client account.
     *
     * <p>The activation email is the credential. It is completed through the admin-side
     * activation endpoint, which issues no tokens — an email link must never mint an admin
     * session directly.
     *
     * <p>Deliberately shares {@link #emailAccess} with the member path: the token, its expiry,
     * and the credential state transitions are the same mechanism. A second copy of that logic
     * would be the copy that misses the next fix.
     */
    public CredentialAccessMaterial initializeForOperator(User user) {
        requireOperatorEmail(user);
        return persisted(user, emailAccess(
                user, null, PLATFORM_ORGANISATION_NAME, CredentialTokenPurpose.ACTIVATION, true));
    }

    /**
     * Re-sends operator activation. Issues a fresh token, so any previously emailed link stops
     * working — the old one cannot be left live alongside the new one.
     */
    public CredentialAccessMaterial resendOperatorActivation(User user) {
        requireOperatorEmail(user);
        if (user.getCredentialState() == CredentialState.ACTIVE) {
            throw new InvalidStateException("This operator has already activated their access");
        }
        tokenSessionService.revokeAll(List.of(user.getId()), TokenAudience.ADMIN);
        return persisted(user, emailAccess(
                user, null, PLATFORM_ORGANISATION_NAME, CredentialTokenPurpose.ACTIVATION, true));
    }

    /**
     * Explicit fallback for when email delivery genuinely fails. Deliberately not issued
     * alongside the activation link: two live credentials for one account, one of which has to
     * travel out of band, is the pair that leaks.
     */
    public CredentialAccessMaterial generateOperatorTemporaryAccess(User user) {
        tokenSessionService.revokeAll(List.of(user.getId()), TokenAudience.ADMIN);
        return persisted(user, temporaryAccess(user));
    }

    /**
     * Operator-initiated recovery. Requires a verified email — that is, one the operator has
     * already proven they can receive mail at by following an activation link. An operator who
     * was set up with a placeholder address and a handed-over temporary password never verified
     * anything, so recovery for them stays with an authorized colleague instead.
     *
     * <p>Does not block the existing password: requesting a reset must not lock someone out.
     */
    public CredentialAccessMaterial requestOperatorSelfServiceReset(User user) {
        if (!hasEmail(user) || !user.isEmailVerified()) {
            throw new InvalidStateException("Verified email is required for self-service password recovery");
        }
        return emailAccess(
                user, null, PLATFORM_ORGANISATION_NAME, CredentialTokenPurpose.PASSWORD_RESET, false);
    }

    private void requireOperatorEmail(User user) {
        if (!hasEmail(user)) {
            throw new InvalidStateException("A platform operator requires an email address");
        }
    }

    public CredentialAccessMaterial regenerate(User user, Account account) {
        if (user.getCredentialState() == CredentialState.ACTIVE) {
            throw new InvalidStateException("Activated access must be reset, not regenerated");
        }
        tokenSessionService.revokeAll(List.of(user.getId()), TokenAudience.CLIENT);
        CredentialTokenPurpose purpose = user.isEmailVerified()
                ? CredentialTokenPurpose.PASSWORD_RESET
                : CredentialTokenPurpose.ACTIVATION;
        return persisted(user, hasEmail(user)
                ? emailAccess(user, requirePersistedAccount(account), account.getName(), purpose, true)
                : temporaryAccess(user));
    }

    public CredentialAccessMaterial reset(User user, Account account) {
        if (user.getCredentialState() != CredentialState.ACTIVE) {
            throw new InvalidStateException("Unactivated access must be regenerated, not reset");
        }
        tokenSessionService.revokeAll(List.of(user.getId()), TokenAudience.CLIENT);
        return persisted(user, hasEmail(user)
                ? emailAccess(user, requirePersistedAccount(account), account.getName(),
                        CredentialTokenPurpose.PASSWORD_RESET, true)
                : temporaryAccess(user));
    }

    public CredentialAccessMaterial requestSelfServiceReset(User user, Account account) {
        if (!hasEmail(user) || !user.isEmailVerified()) {
            throw new InvalidStateException("Verified email is required for self-service password recovery");
        }
        return emailAccess(user, requirePersistedAccount(account), account.getName(),
                CredentialTokenPurpose.PASSWORD_RESET, false);
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
        userRepository.saveAndFlush(user);
    }

    public void unlock(User user) {
        if (!user.isInitialAccessLocked()) {
            throw new InvalidStateException("Initial access is not locked");
        }
        user.setInitialAccessLocked(false);
        user.setInitialAccessFailedAttempts(0);
        userRepository.saveAndFlush(user);
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
                null,
                null);
    }

    /**
     * @param accountId owning client account, or null for a platform-scope delivery
     * @param organisationName name shown to the recipient — the client account, or the platform
     */
    private CredentialAccessMaterial emailAccess(
            User user,
            UUID accountId,
            String organisationName,
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
        if (user.getId() == null) {
            throw new IllegalStateException("Credential email delivery requires persisted identities");
        }
        var deliveryId = emailDeliveryTracker.queue(
                accountId, user.getId(), user.getEmail(), purpose);
        eventPublisher.publishEvent(new CredentialEmailRequestedEvent(
                deliveryId, user.getEmail(), user.getFullName(), organisationName, rawToken,
                purpose, expiresAt,
                user.getKind() == IdentityKind.PLATFORM
                        ? CredentialEmailRequestedEvent.CredentialAudience.PLATFORM_OPERATOR
                        : CredentialEmailRequestedEvent.CredentialAudience.CLIENT));
        return new CredentialAccessMaterial(
                InitialAccessMethod.EMAIL_LINK,
                user.getCredentialState(),
                null,
                expiresAt,
                deliveryId);
    }

    public void clearToken(User user) {
        user.setCredentialTokenHash(null);
        user.setCredentialTokenPurpose(null);
        user.setCredentialTokenExpiresAt(null);
    }

    private boolean hasEmail(User user) {
        return user.getEmail() != null && !user.getEmail().isBlank();
    }

    /** The member paths still require a persisted account; only operators may pass none. */
    private static UUID requirePersistedAccount(Account account) {
        if (account == null || account.getId() == null) {
            throw new IllegalStateException("Credential email delivery requires persisted identities");
        }
        return account.getId();
    }

    /**
     * Identity persists its own credential-state changes. Callers in other domains must not
     * write to the user row themselves.
     */
    private CredentialAccessMaterial persisted(User user, CredentialAccessMaterial material) {
        userRepository.saveAndFlush(user);
        return material;
    }
}
