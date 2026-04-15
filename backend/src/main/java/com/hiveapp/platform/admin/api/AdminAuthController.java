package com.hiveapp.platform.admin.api;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hiveapp.identity.dto.AuthResponse;
import com.hiveapp.identity.dto.LoginRequest;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.shared.exception.UnauthorizedException;
import com.hiveapp.shared.security.JwtTokenProvider;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Slf4j
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        // Look up by email directly — no findAll()
        var adminUser = adminUserRepository.findByUser_Email(request.email())
            .orElseThrow(() -> new UnauthorizedException("Invalid admin email or password"));

        if (!adminUser.isActive() || !adminUser.getUser().isActive()) {
            throw new UnauthorizedException("Admin account is inactive");
        }

        if (!passwordEncoder.matches(request.password(), adminUser.getUser().getPasswordHash())) {
            throw new UnauthorizedException("Invalid admin email or password");
        }

        log.info("Admin logged in: {}", adminUser.getUser().getEmail());

        // Token subject = User.id (consistent with AdminUserDetailsServiceImpl and AdminPermissionPolicy)
        var claims = Map.<String, Object>of("tokenType", "ADMIN");
        String accessToken = jwtTokenProvider.generateAccessToken(adminUser.getUser().getId(), claims);
        String refreshToken = jwtTokenProvider.generateRefreshToken(adminUser.getUser().getId());
        long expiresIn = jwtTokenProvider.getAccessTokenExpiration();

        return AuthResponse.of(accessToken, refreshToken, expiresIn);
    }
}
