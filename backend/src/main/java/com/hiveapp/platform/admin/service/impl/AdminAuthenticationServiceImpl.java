package com.hiveapp.platform.admin.service.impl;

import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.identity.dto.AuthResponse;
import com.hiveapp.identity.dto.LoginRequest;
import com.hiveapp.identity.dto.RefreshTokenRequest;
import com.hiveapp.identity.service.CredentialAuthenticationService;
import com.hiveapp.platform.admin.domain.entity.AdminUser;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.platform.admin.dto.AdminMeDto;
import com.hiveapp.platform.admin.service.AdminPermissionResolver;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import java.util.UUID;
import com.hiveapp.platform.admin.service.AdminAuthenticationService;
import com.hiveapp.shared.exception.UnauthorizedException;
import com.hiveapp.shared.security.IssuedTokens;
import com.hiveapp.shared.security.TokenAudience;
import com.hiveapp.shared.security.TokenSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthenticationServiceImpl implements AdminAuthenticationService {

    private final CredentialAuthenticationService credentialAuthenticationService;
    private final AdminUserRepository adminUserRepository;
    private final TokenSessionService tokenSessionService;
    private final AdminPermissionResolver adminPermissionResolver;

    @Override
    @Transactional(readOnly = true)
    public AdminMeDto getAdminDetails(UUID userId) {
        var admin = adminUserRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("AdminUser", "userId", userId));
        return new AdminMeDto(
                admin.getId(),
                admin.getUser().getEmail(),
                admin.isSuperAdmin(),
                admin.isActive(),
                adminPermissionResolver.resolve(admin));
    }

    /**
     * Writable: a temporary password is consumed on first use, and a read-only transaction would
     * silently discard that write, leaving the handed-over password replayable forever.
     */
    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = credentialAuthenticationService.authenticate(
                request.identifier(),
                request.password(),
                "Invalid admin email or password",
                "Admin account is inactive");
        AdminUser admin = requireActiveAdmin(user);

        // A temporary password is consumed here: it works once, and until the operator chooses
        // their own the session is restricted to the change itself. Issuing a full ADMIN session
        // would let a handed-over password operate the platform indefinitely.
        if (credentialAuthenticationService.consumeTemporaryPasswordIfPresent(
                user.getId(), request.password())) {
            tokenSessionService.revokeAll(java.util.List.of(user.getId()), TokenAudience.ADMIN);
            var initial = tokenSessionService.issueInitialAccess(user.getId(), TokenAudience.ADMIN);
            log.info("Admin must change their initial password: {}", admin.getUser().getEmail());
            return AuthResponse.initialAccess(initial.accessToken(), initial.expiresIn());
        }

        log.info("Admin logged in: {}", admin.getUser().getEmail());
        return issueTokens(admin.getUser());
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse refresh(RefreshTokenRequest request) {
        var identity = tokenSessionService.consume(request.refreshToken(), TokenAudience.ADMIN);
        // The admin domain owns this lookup; identity is not consulted at all.
        AdminUser admin = requireActiveAdminByUserId(identity.userId());
        log.info("Admin token refreshed: {}", admin.getUser().getEmail());
        return issueTokens(admin.getUser());
    }

    @Override
    public void logout(RefreshTokenRequest request) {
        tokenSessionService.revoke(request.refreshToken(), TokenAudience.ADMIN);
    }

    private AdminUser requireActiveAdmin(User user) {
        return requireActiveAdminByUserId(user.getId());
    }

    /**
     * Resolves the administrator from its own aggregate. The user's active flag is read through
     * the AdminUser relationship rather than by querying identity.
     */
    private AdminUser requireActiveAdminByUserId(java.util.UUID userId) {
        AdminUser admin = adminUserRepository.findByUserId(userId)
                .orElseThrow(() -> new UnauthorizedException("Invalid admin email or password"));
        if (!admin.isActive() || !admin.getUser().isActive()) {
            throw new UnauthorizedException("Admin account is inactive");
        }
        return admin;
    }

    private AuthResponse issueTokens(User user) {
        IssuedTokens tokens = tokenSessionService.issue(user.getId(), TokenAudience.ADMIN);
        return AuthResponse.of(tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn());
    }
}
