package com.hiveapp.platform.admin.api;

import com.hiveapp.identity.dto.LoginRequest;
import com.hiveapp.identity.dto.AuthResponse;
import com.hiveapp.identity.dto.InitialPasswordChangeRequest;
import com.hiveapp.identity.dto.PasswordCompletionRequest;
import com.hiveapp.identity.dto.PasswordResetRequest;
import com.hiveapp.identity.dto.RefreshTokenRequest;
import com.hiveapp.identity.dto.CredentialTokenRequest;
import com.hiveapp.identity.service.CredentialLifecycleService;
import com.hiveapp.platform.admin.service.AdminAuthenticationService;
import com.hiveapp.platform.admin.dto.AdminMeDto;
import com.hiveapp.shared.security.HiveAppUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthenticationService adminAuthenticationService;
    private final CredentialLifecycleService credentialLifecycleService;
    private final com.hiveapp.shared.email.delivery.EmailDeliveryTracker emailDeliveryTracker;

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

    @PostMapping("/auth/email-verification/complete")
    public ResponseEntity<Void> completeEmailVerification(
            @Valid @RequestBody CredentialTokenRequest request) {
        credentialLifecycleService.completeOperatorEmailVerification(request.token());
        return ResponseEntity.noContent().build();
    }

    /**
     * Completes the change forced after a temporary password, and only then issues a normal
     * admin session. Mirrors the client flow: the restricted token travels as a Bearer
     * credential, not in the body, so it is never logged as request content.
     */
    @PostMapping("/auth/initial-password/change")
    public AuthResponse completeInitialPassword(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody InitialPasswordChangeRequest request
    ) {
        return credentialLifecycleService.completeOperatorInitialPassword(
                bearerToken(authorization), request.newPassword());
    }

    /**
     * Abandons a pending change. Without this an operator whose restricted token was consumed or
     * expired mid-flow has no way out of the change screen except waiting for expiry.
     */
    @PostMapping("/auth/initial-password/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutInitialAccess(@RequestHeader("Authorization") String authorization) {
        credentialLifecycleService.logoutOperatorInitialAccess(bearerToken(authorization));
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new com.hiveapp.shared.exception.UnauthorizedException("Initial-access session is invalid");
        }
        return authorization.substring(7).trim();
    }

    @GetMapping("/me")
    public ResponseEntity<AdminMeDto> getMe(@AuthenticationPrincipal HiveAppUserDetails userDetails) {
        return ResponseEntity.ok(adminAuthenticationService.getAdminDetails(userDetails.getUserId()));
    }

    @PostMapping("/me/email-verification")
    public com.hiveapp.platform.admin.dto.AdminOperatorAccessResponse requestOwnEmailVerification(
            @AuthenticationPrincipal HiveAppUserDetails userDetails) {
        var response = adminAuthenticationService.requestOwnEmailVerification(userDetails.getUserId());
        return response.emailDeliveryId() == null
                ? response
                : response.withEmailDelivery(
                        emailDeliveryTracker.findSummary(response.emailDeliveryId()).orElse(null));
    }
}
