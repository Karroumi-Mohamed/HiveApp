package com.hiveapp.identity.api;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hiveapp.identity.dto.AuthResponse;
import com.hiveapp.identity.dto.LoginRequest;
import com.hiveapp.identity.dto.RefreshTokenRequest;
import com.hiveapp.identity.dto.RegisterRequest;
import com.hiveapp.identity.dto.InitialPasswordChangeRequest;
import com.hiveapp.identity.dto.PasswordCompletionRequest;
import com.hiveapp.identity.dto.PasswordResetRequest;
import com.hiveapp.identity.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;


@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(code = HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/activation/complete")
    public AuthResponse completeActivation(@Valid @RequestBody PasswordCompletionRequest request) {
        return authService.completeActivation(request.token(), request.newPassword());
    }

    @PostMapping("/initial-password/change")
    public AuthResponse changeInitialPassword(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody InitialPasswordChangeRequest request
    ) {
        return authService.changeInitialPassword(bearerToken(authorization), request.newPassword());
    }

    @PostMapping("/initial-password/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutInitialAccess(@RequestHeader("Authorization") String authorization) {
        authService.logoutInitialAccess(bearerToken(authorization));
    }

    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        authService.requestPasswordReset(request.email());
    }

    @PostMapping("/password-reset/complete")
    public AuthResponse completePasswordReset(@Valid @RequestBody PasswordCompletionRequest request) {
        return authService.completePasswordReset(request.token(), request.newPassword());
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new com.hiveapp.shared.exception.UnauthorizedException("Initial-access token is required");
        }
        return authorization.substring(7);
    }
}
