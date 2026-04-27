package com.hiveapp.platform.admin.api;

import com.hiveapp.identity.dto.LoginRequest;
import com.hiveapp.identity.dto.AuthResponse;
import com.hiveapp.platform.admin.domain.entity.AdminUser;
import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.platform.admin.service.AdminUserService;
import com.hiveapp.platform.admin.dto.AdminMeDto;
import com.hiveapp.shared.security.JwtTokenProvider;
import com.hiveapp.shared.security.HiveAppUserDetails;
import com.hiveapp.shared.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminUserRepository adminUserRepository;
    private final AdminUserService adminUserService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/auth/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        AdminUser admin = adminUserRepository.findByUser_Email(request.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid admin email or password"));

        if (!admin.isActive() || !admin.getUser().isActive()) {
            throw new UnauthorizedException("Admin account is inactive");
        }

        if (!passwordEncoder.matches(request.password(), admin.getUser().getPasswordHash())) {
            throw new UnauthorizedException("Invalid admin email or password");
        }

        log.info("Admin logged in: {}", admin.getUser().getEmail());

        var claims = Map.<String, Object>of("tokenType", "ADMIN");
        String accessToken = jwtTokenProvider.generateAccessToken(admin.getUser().getId(), claims);
        String refreshToken = jwtTokenProvider.generateRefreshToken(admin.getUser().getId());
        long expiresIn = jwtTokenProvider.getAccessTokenExpiration();

        return AuthResponse.of(accessToken, refreshToken, expiresIn);
    }

    @GetMapping("/me")
    public ResponseEntity<AdminMeDto> getMe(@AuthenticationPrincipal HiveAppUserDetails userDetails) {
        return ResponseEntity.ok(adminUserService.getAdminDetails(userDetails.getUserId()));
    }
}
