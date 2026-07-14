package com.hiveapp.platform.admin.api;

import com.hiveapp.identity.dto.LoginRequest;
import com.hiveapp.identity.dto.AuthResponse;
import com.hiveapp.identity.dto.RefreshTokenRequest;
import com.hiveapp.platform.admin.service.AdminAuthenticationService;
import com.hiveapp.platform.admin.service.AdminUserService;
import com.hiveapp.platform.admin.dto.AdminMeDto;
import com.hiveapp.shared.security.HiveAppUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthenticationService adminAuthenticationService;
    private final AdminUserService adminUserService;

    @PostMapping("/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return adminAuthenticationService.login(request);
    }

    @PostMapping("/auth/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return adminAuthenticationService.refresh(request);
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        adminAuthenticationService.logout(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<AdminMeDto> getMe(@AuthenticationPrincipal HiveAppUserDetails userDetails) {
        return ResponseEntity.ok(adminUserService.getAdminDetails(userDetails.getUserId()));
    }
}
