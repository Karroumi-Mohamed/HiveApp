package com.hiveapp.identity.service;

import com.hiveapp.identity.domain.EmailIdentity;
import com.hiveapp.identity.domain.constant.CredentialState;
import com.hiveapp.identity.domain.constant.CredentialTokenPurpose;
import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.identity.dto.AuthResponse;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.exception.UnauthorizedException;
import com.hiveapp.shared.security.TokenAudience;
import com.hiveapp.shared.security.TokenSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CredentialLifecycleService {

    private static final String INVALID_LINK = "Invalid or expired credential link";

    private final UserRepository userRepository;
    private final MemberRepository memberRepository;
    private final CredentialSecretGenerator secretGenerator;
    private final MemberCredentialService memberCredentialService;
    private final PasswordEncoder passwordEncoder;
    private final TokenSessionService tokenSessionService;

    @Transactional
    public AuthResponse completeActivation(String token, String newPassword) {
        User user = tokenUser(token, CredentialTokenPurpose.ACTIVATION);
        if (user.getCredentialState() != CredentialState.EMAIL_ACTIVATION_PENDING) {
            throw new InvalidStateException(INVALID_LINK);
        }
        requireActiveMembership(user);
        activatePassword(user, newPassword);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse completePasswordReset(String token, String newPassword) {
        User user = tokenUser(token, CredentialTokenPurpose.PASSWORD_RESET);
        requireActiveMembership(user);
        activatePassword(user, newPassword);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse changeInitialPassword(String initialAccessToken, String newPassword) {
        var userId = tokenSessionService.consumeInitialAccess(initialAccessToken);
        User user = userRepository.findByIdForCredentialUpdate(userId)
                .orElseThrow(() -> new UnauthorizedException("Initial-access session is invalid"));
        if (user.getCredentialState() != CredentialState.INITIAL_PASSWORD_CHANGE
                || !user.isPasswordChangeRequired()) {
            throw new UnauthorizedException("Initial-access session is invalid");
        }
        requireActiveMembership(user);
        activatePassword(user, newPassword);
        return issueTokens(user);
    }

    public void logoutInitialAccess(String initialAccessToken) {
        tokenSessionService.consumeInitialAccess(initialAccessToken);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        User user = userRepository.findByEmail(EmailIdentity.canonicalize(email)).orElse(null);
        if (user == null || !user.isActive() || !user.isEmailVerified()) {
            return;
        }
        var member = memberRepository.findByUserIdAndIsActiveTrue(user.getId()).orElse(null);
        if (member == null || !member.getAccount().isActive()) {
            return;
        }
        memberCredentialService.requestSelfServiceReset(user, member.getAccount());
        userRepository.saveAndFlush(user);
    }

    private User tokenUser(String rawToken, CredentialTokenPurpose purpose) {
        String hash = secretGenerator.hashToken(rawToken);
        User user = userRepository.findByCredentialTokenHashForUpdate(hash)
                .orElseThrow(() -> new InvalidStateException(INVALID_LINK));
        if (user.getCredentialTokenPurpose() != purpose
                || user.getCredentialTokenExpiresAt() == null
                || !user.getCredentialTokenExpiresAt().isAfter(Instant.now())) {
            throw new InvalidStateException(INVALID_LINK);
        }
        return user;
    }

    private Account requireActiveMembership(User user) {
        var member = memberRepository.findByUserIdAndIsActiveTrue(user.getId())
                .orElseThrow(() -> new InvalidStateException("Workspace membership is inactive"));
        if (!member.getAccount().isActive()) {
            throw new InvalidStateException("Workspace account is suspended");
        }
        return member.getAccount();
    }

    private void activatePassword(User user, String newPassword) {
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setCredentialState(CredentialState.ACTIVE);
        user.setPasswordChangeRequired(false);
        user.setEmailVerified(user.getEmail() != null || user.isEmailVerified());
        user.setInitialAccessFailedAttempts(0);
        user.setInitialAccessLocked(false);
        memberCredentialService.clearToken(user);
        userRepository.saveAndFlush(user);
        tokenSessionService.revokeAll(List.of(user.getId()), TokenAudience.CLIENT);
    }

    private AuthResponse issueTokens(User user) {
        var tokens = tokenSessionService.issue(user.getId(), TokenAudience.CLIENT);
        return AuthResponse.of(tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn());
    }
}
