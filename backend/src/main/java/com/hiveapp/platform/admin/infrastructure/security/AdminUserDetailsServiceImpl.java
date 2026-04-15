package com.hiveapp.platform.admin.infrastructure.security;

import java.util.UUID;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hiveapp.platform.admin.domain.repository.AdminUserRepository;
import com.hiveapp.shared.security.HiveAppUserDetails;

import lombok.RequiredArgsConstructor;

@Service("adminUserDetailsService")
@RequiredArgsConstructor
public class AdminUserDetailsServiceImpl implements UserDetailsService{
    private final AdminUserRepository adminUserRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        // userId = User.id (the token subject set by AdminAuthController)
        var adminUser = adminUserRepository.findByUserId(UUID.fromString(userId))
            .orElseThrow(() -> new UsernameNotFoundException("Admin user not found for userId: " + userId));

        // Store User.id in HiveAppUserDetails so ContextDetectionFilter and AdminPermissionPolicy
        // see the correct actorUserId and can call adminUserRepository.findByUserId() consistently
        return new HiveAppUserDetails(
            adminUser.getUser().getId(),
            adminUser.getUser().getEmail(),
            adminUser.getUser().getPasswordHash(),
            adminUser.isActive() && adminUser.getUser().isActive()
        );
    }
}
