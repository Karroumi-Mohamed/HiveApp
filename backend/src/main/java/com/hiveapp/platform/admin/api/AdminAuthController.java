package com.hiveapp.platform.admin.api;

import com.hiveapp.identity.dto.LoginRequest;
import com.hiveapp.identity.dto.AuthResponse;
import com.hiveapp.identity.dto.PasswordCompletionRequest;
import com.hiveapp.identity.dto.PasswordResetRequest;
import com.hiveapp.identity.dto.RefreshTokenRequest;
import com.hiveapp.identity.service.CredentialLifecycleService;
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
    private final CredentialLifecycleService credentialLifecycleService;

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

    /**
     * Completes operator activation from an emailed link. Returns no session deliberately — the
     * operator sets their password here and then signs in through the normal admin login, so an
     * email link can never by itself produce an authenticated admin session.
     */
    @PostMapping("/auth/activation/complete")
    public ResponseEntity<Void> completeActivation(@Valid @RequestBody PasswordCompletionRequest request) {
        credentialLifecycleService.completeOperatorActivation(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    /**
     * Always 204, whether or not the address belongs to an operator — the response must not
     * reveal which emails hold platform access.
     */
    @PostMapping("/auth/password-reset/request")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        credentialLifecycleService.requestOperatorPasswordReset(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/password-reset/complete")
    public ResponseEntity<Void> completePasswordReset(@Valid @RequestBody PasswordCompletionRequest request) {
        credentialLifecycleService.completeOperatorPasswordReset(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<AdminMeDto> getMe(@AuthenticationPrincipal HiveAppUserDetails userDetails) {
        return ResponseEntity.ok(adminAuthenticationService.getAdminDetails(userDetails.getUserId()));
    }
}
