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
import com.hiveapp.shared.audit.AuditTrail;
import com.hiveapp.shared.audit.AuditedMutation;
import com.hiveapp.shared.audit.domain.AuditActorSurface;
import com.hiveapp.shared.security.TokenAudience;
import com.hiveapp.shared.security.TokenSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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
    private final AuditTrail auditTrail;

    @Transactional
    @AuditedMutation(
            action = "identity.credentials.activation.complete",
            resourceType = "USER",
            recordSuccess = false)
    public AuthResponse completeActivation(String token, String newPassword) {
        User user = tokenUser(token, CredentialTokenPurpose.ACTIVATION);
        CredentialState before = user.getCredentialState();
        if (user.getCredentialState() != CredentialState.EMAIL_ACTIVATION_PENDING) {
            throw new InvalidStateException(INVALID_LINK);
        }
        Account account = requireActiveMembership(user);
        activatePassword(user, newPassword);
        auditCredentialChange(
                "identity.credentials.activation.complete", user, account, before, user.getId());
        return issueTokens(user);
    }

    @Transactional
    @AuditedMutation(
            action = "identity.credentials.password_reset.complete",
            resourceType = "USER",
            recordSuccess = false)
    public AuthResponse completePasswordReset(String token, String newPassword) {
        User user = tokenUser(token, CredentialTokenPurpose.PASSWORD_RESET);
        CredentialState before = user.getCredentialState();
        Account account = requireActiveMembership(user);
        activatePassword(user, newPassword);
        auditCredentialChange(
                "identity.credentials.password_reset.complete", user, account, before, user.getId());
        return issueTokens(user);
    }

    @Transactional
    @AuditedMutation(
            action = "identity.credentials.initial_password.complete",
            resourceType = "USER",
            recordSuccess = false)
    public AuthResponse changeInitialPassword(String initialAccessToken, String newPassword) {
        var userId = tokenSessionService.consumeInitialAccess(initialAccessToken);
        User user = userRepository.findByIdForCredentialUpdate(userId)
                .orElseThrow(() -> new UnauthorizedException("Initial-access session is invalid"));
        if (user.getCredentialState() != CredentialState.INITIAL_PASSWORD_CHANGE
                || !user.isPasswordChangeRequired()) {
            throw new UnauthorizedException("Initial-access session is invalid");
        }
        CredentialState before = user.getCredentialState();
        Account account = requireActiveMembership(user);
        activatePassword(user, newPassword);
        auditCredentialChange(
                "identity.credentials.initial_password.complete", user, account, before, user.getId());
        return issueTokens(user);
    }

    public void logoutInitialAccess(String initialAccessToken) {
        tokenSessionService.consumeInitialAccess(initialAccessToken);
    }

    @Transactional
    @AuditedMutation(
            action = "identity.credentials.password_reset.request",
            resourceType = "USER",
            recordSuccess = false)
    public void requestPasswordReset(String email) {
        User user = userRepository.findByEmail(EmailIdentity.canonicalize(email)).orElse(null);
        if (user == null || !user.isActive() || !user.isEmailVerified()) {
            return;
        }
        var member = memberRepository.findByUserIdAndIsActiveTrue(user.getId()).orElse(null);
        if (member == null || !member.getAccount().isActive()) {
            return;
        }
        CredentialState before = user.getCredentialState();
        memberCredentialService.requestSelfServiceReset(user, member.getAccount());
        userRepository.saveAndFlush(user);
        auditTrail.recordSuccess(
                "identity.credentials.password_reset.request",
                "USER",
                user.getId(),
                AuditActorSurface.SYSTEM,
                null,
                member.getAccount().getId(),
                Map.of("credentialState", before),
                Map.of(
                        "credentialState", user.getCredentialState(),
                        "credentialTokenPurpose", user.getCredentialTokenPurpose(),
                        "credentialTokenExpiresAt", user.getCredentialTokenExpiresAt()));
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

    private void auditCredentialChange(
            String action,
            User user,
            Account account,
            CredentialState before,
            java.util.UUID actorUserId
    ) {
        auditTrail.recordSuccess(
                action,
                "USER",
                user.getId(),
                AuditActorSurface.CLIENT_WORKSPACE,
                actorUserId,
                account.getId(),
                Map.of("credentialState", before),
                Map.of(
                        "credentialState", user.getCredentialState(),
                        "passwordChangeRequired", user.isPasswordChangeRequired(),
                        "emailVerified", user.isEmailVerified()));
    }

    private AuthResponse issueTokens(User user) {
        var tokens = tokenSessionService.issue(user.getId(), TokenAudience.CLIENT);
        return AuthResponse.of(tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn());
    }
}
