package com.hiveapp.platform.admin.service.impl;

import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.identity.dto.AuthResponse;
import com.hiveapp.identity.dto.LoginRequest;
import com.hiveapp.identity.dto.RefreshTokenRequest;
import com.hiveapp.identity.service.CredentialAuthenticationService;
import com.hiveapp.platform.admin.domain.entity.AdminUser;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
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
    private final UserRepository userRepository;
    private final TokenSessionService tokenSessionService;

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = credentialAuthenticationService.authenticate(
                request.email(),
                request.password(),
                "Invalid admin email or password",
                "Admin account is inactive");
        AdminUser admin = requireActiveAdmin(user);
        log.info("Admin logged in: {}", admin.getUser().getEmail());
        return issueTokens(admin.getUser());
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse refresh(RefreshTokenRequest request) {
        var identity = tokenSessionService.consume(request.refreshToken(), TokenAudience.ADMIN);
        User user = userRepository.findById(identity.userId())
                .orElseThrow(() -> new UnauthorizedException("Admin account not found"));
        AdminUser admin = requireActiveAdmin(user);
        log.info("Admin token refreshed: {}", admin.getUser().getEmail());
        return issueTokens(admin.getUser());
    }

    @Override
    public void logout(RefreshTokenRequest request) {
        tokenSessionService.revoke(request.refreshToken(), TokenAudience.ADMIN);
    }

    private AdminUser requireActiveAdmin(User user) {
        AdminUser admin = adminUserRepository.findByUserId(user.getId())
                .orElseThrow(() -> new UnauthorizedException("Invalid admin email or password"));
        if (!admin.isActive() || !user.isActive()) {
            throw new UnauthorizedException("Admin account is inactive");
        }
        return admin;
    }

    private AuthResponse issueTokens(User user) {
        IssuedTokens tokens = tokenSessionService.issue(user.getId(), TokenAudience.ADMIN);
        return AuthResponse.of(tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn());
    }
}
