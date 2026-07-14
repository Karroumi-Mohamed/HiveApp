package com.hiveapp.platform.admin.service;

import com.hiveapp.identity.dto.AuthResponse;
import com.hiveapp.identity.dto.LoginRequest;
import com.hiveapp.identity.dto.RefreshTokenRequest;

public interface AdminAuthenticationService {
    AuthResponse login(LoginRequest request);
    AuthResponse refresh(RefreshTokenRequest request);
    void logout(RefreshTokenRequest request);
}
