package com.hiveapp.platform.admin.service;

import com.hiveapp.identity.dto.AuthResponse;
import com.hiveapp.identity.dto.LoginRequest;
import com.hiveapp.identity.dto.RefreshTokenRequest;

public interface AdminAuthenticationService {
    AuthResponse login(LoginRequest request);

    /**
     * Who the caller is and what they may do.
     *
     * <p>Lives with the session operations, not with admin-user management, because it must not
     * be permission-guarded: this is the call that *reports* permissions, so requiring one to
     * read it is circular. An operator holding no role yet — every operator in the moment after
     * they activate — could otherwise sign in and then be refused their own profile.
     */
    com.hiveapp.platform.admin.dto.AdminMeDto getAdminDetails(java.util.UUID userId);
    AuthResponse refresh(RefreshTokenRequest request);
    void logout(RefreshTokenRequest request);
}
