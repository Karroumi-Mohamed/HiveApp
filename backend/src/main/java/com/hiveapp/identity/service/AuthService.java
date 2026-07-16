package com.hiveapp.identity.service;

import com.hiveapp.identity.dto.AuthResponse;
import com.hiveapp.identity.dto.LoginRequest;
import com.hiveapp.identity.dto.RefreshTokenRequest;
import com.hiveapp.identity.dto.RegisterRequest;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    AuthResponse refresh(RefreshTokenRequest request);
    void logout(RefreshTokenRequest request);
    AuthResponse completeActivation(String token, String newPassword);
    AuthResponse changeInitialPassword(String initialAccessToken, String newPassword);
    void logoutInitialAccess(String initialAccessToken);
    void requestPasswordReset(String email);
    AuthResponse completePasswordReset(String token, String newPassword);
}
