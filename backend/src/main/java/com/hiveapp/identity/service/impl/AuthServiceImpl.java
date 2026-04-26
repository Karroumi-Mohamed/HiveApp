package com.hiveapp.identity.service.impl;

import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.identity.dto.AuthResponse;
import com.hiveapp.identity.dto.LoginRequest;
import com.hiveapp.identity.dto.RefreshTokenRequest;
import com.hiveapp.identity.dto.RegisterRequest;
import com.hiveapp.identity.service.AuthService;
import com.hiveapp.platform.client.account.service.WorkspaceProvisioningService;
import com.hiveapp.shared.exception.DuplicateResourceException;
import com.hiveapp.shared.exception.UnauthorizedException;
import com.hiveapp.shared.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final WorkspaceProvisioningService workspaceProvisioningService;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("User", "email", request.email());
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phone(request.phone())
                .build();

        user = userRepository.save(user);
        log.info("Registered new user: {}", user.getEmail());

        // Provision workspace synchronously — no events needed
        workspaceProvisioningService.provision(user.getId(), user.getEmail());

        return issueTokens(user);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!user.isActive()) {
            throw new UnauthorizedException("Account is deactivated");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getId().toString(), request.password()));
        } catch (BadCredentialsException ex) {
            throw new UnauthorizedException("Invalid email or password");
        }

        log.info("User logged in: {}", user.getEmail());
        return issueTokens(user);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse refresh(RefreshTokenRequest request) {
        if (!jwtTokenProvider.validateToken(request.refreshToken())) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        var userId = jwtTokenProvider.getUserIdFromToken(request.refreshToken());
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        if (!user.isActive()) {
            throw new UnauthorizedException("Account is deactivated");
        }

        log.info("Token refreshed for user: {}", user.getEmail());
        return issueTokens(user);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private AuthResponse issueTokens(User user) {
        var claims = Map.<String, Object>of("tokenType", "CLIENT");
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), claims);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        long expiresIn = jwtTokenProvider.getAccessTokenExpiration();
        return AuthResponse.of(accessToken, refreshToken, expiresIn);
    }
}
