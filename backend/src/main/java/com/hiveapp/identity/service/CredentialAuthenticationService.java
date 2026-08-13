package com.hiveapp.identity.service;

import com.hiveapp.identity.domain.EmailIdentity;
import com.hiveapp.identity.domain.constant.CredentialState;
import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.shared.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CredentialAuthenticationService {

    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Rejects credential states that must be completed elsewhere, and consumes a temporary
     * password on its first successful use.
     *
     * <p>Consuming means clearing the stored hash and moving the identity to
     * {@link CredentialState#INITIAL_PASSWORD_CHANGE}: the handed-over password works exactly
     * once, and cannot be replayed by anyone who saw it in transit. The caller is expected to
     * issue a restricted session and require the change before granting a normal one.
     *
     * @return true when a password change is now pending for this identity
     */
    @Transactional
    public boolean consumeTemporaryPasswordIfPresent(UUID userId, String rawPassword) {
        User locked = userRepository.findByIdForCredentialUpdate(userId)
                .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS));

        if (locked.getCredentialState() == CredentialState.EMAIL_ACTIVATION_PENDING
                || locked.getCredentialState() == CredentialState.EMAIL_RESET_PENDING) {
            throw new UnauthorizedException("Email activation or password reset must be completed first");
        }
        if (locked.getCredentialState() == CredentialState.INITIAL_PASSWORD_CHANGE) {
            // The hash was cleared when it was first used, so there is nothing left to match.
            throw new UnauthorizedException(
                    "Temporary access was already used. Ask an administrator to generate new access.");
        }
        if (locked.getCredentialState() != CredentialState.TEMPORARY_PASSWORD) {
            return false;
        }
        if (locked.isInitialAccessLocked()
                || locked.getPasswordHash() == null
                || !passwordEncoder.matches(rawPassword, locked.getPasswordHash())) {
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }

        locked.setPasswordHash(null);
        locked.setCredentialState(CredentialState.INITIAL_PASSWORD_CHANGE);
        locked.setPasswordChangeRequired(true);
        locked.setInitialAccessFailedAttempts(0);
        userRepository.saveAndFlush(locked);
        return true;
    }

    @Transactional(readOnly = true)
    public User authenticate(
            String email,
            String password,
            String invalidCredentialsMessage,
            String inactiveAccountMessage
    ) {
        User user = userRepository.findByEmail(EmailIdentity.canonicalize(email))
                .orElseThrow(() -> new UnauthorizedException(invalidCredentialsMessage));

        if (!user.isActive()) {
            throw new UnauthorizedException(inactiveAccountMessage);
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new UnauthorizedException(invalidCredentialsMessage);
        }
        return user;
    }
}
