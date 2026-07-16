package com.hiveapp.identity.service.impl;

import com.hiveapp.identity.domain.EmailIdentity;
import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.identity.dto.AuthResponse;
import com.hiveapp.identity.dto.LoginRequest;
import com.hiveapp.identity.dto.RefreshTokenRequest;
import com.hiveapp.identity.dto.RegisterRequest;
import com.hiveapp.identity.service.AuthService;
import com.hiveapp.identity.service.ClientCredentialAuthenticationService;
import com.hiveapp.identity.service.CredentialLifecycleService;
import com.hiveapp.platform.client.account.service.WorkspaceProvisioningService;
import com.hiveapp.shared.exception.DuplicateResourceException;
import com.hiveapp.shared.exception.UnauthorizedException;
import com.hiveapp.shared.security.IssuedTokens;
import com.hiveapp.shared.security.TokenAudience;
import com.hiveapp.shared.security.TokenSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ClientCredentialAuthenticationService clientCredentialAuthenticationService;
    private final CredentialLifecycleService credentialLifecycleService;
    private final TokenSessionService tokenSessionService;
    private final WorkspaceProvisioningService workspaceProvisioningService;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = EmailIdentity.canonicalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("User", "email", email);
        }

        User user = User.builder()
                .email(email)
                .username("owner-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 20))
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phone(request.phone())
                .emailVerified(true)
                .build();

        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException("User", "email", email);
        }
        log.info("Registered new user: {}", user.getEmail());

        // Provision workspace synchronously — no events needed
        workspaceProvisioningService.provision(user.getId(), user.getEmail());

        return issueTokens(user);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        var authentication = clientCredentialAuthenticationService.authenticate(request);
        User user = authentication.user();

        log.info("Client identity authenticated: {}", user.getId());
        if (authentication.passwordChangeRequired()) {
            tokenSessionService.revokeAll(java.util.List.of(user.getId()), TokenAudience.CLIENT);
            var initial = tokenSessionService.issueInitialAccess(user.getId());
            return AuthResponse.initialAccess(initial.accessToken(), initial.expiresIn());
        }
        return issueTokens(user);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse refresh(RefreshTokenRequest request) {
        var identity = tokenSessionService.consume(request.refreshToken(), TokenAudience.CLIENT);
        var user = userRepository.findById(identity.userId())
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        if (!user.isActive()) {
            throw new UnauthorizedException("Account is deactivated");
        }
        if (user.getCredentialState() != com.hiveapp.identity.domain.constant.CredentialState.ACTIVE
                || user.isPasswordChangeRequired()) {
            throw new UnauthorizedException("Credential activation is required");
        }

        log.info("Token refreshed for user: {}", user.getEmail());
        return issueTokens(user);
    }

    @Override
    public void logout(RefreshTokenRequest request) {
        tokenSessionService.revoke(request.refreshToken(), TokenAudience.CLIENT);
    }

    @Override
    public AuthResponse completeActivation(String token, String newPassword) {
        return credentialLifecycleService.completeActivation(token, newPassword);
    }

    @Override
    public AuthResponse changeInitialPassword(String initialAccessToken, String newPassword) {
        return credentialLifecycleService.changeInitialPassword(initialAccessToken, newPassword);
    }

    @Override
    public void logoutInitialAccess(String initialAccessToken) {
        credentialLifecycleService.logoutInitialAccess(initialAccessToken);
    }

    @Override
    public void requestPasswordReset(String email) {
        credentialLifecycleService.requestPasswordReset(email);
    }

    @Override
    public AuthResponse completePasswordReset(String token, String newPassword) {
        return credentialLifecycleService.completePasswordReset(token, newPassword);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private AuthResponse issueTokens(User user) {
        IssuedTokens tokens = tokenSessionService.issue(user.getId(), TokenAudience.CLIENT);
        return AuthResponse.of(tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn());
    }
}
